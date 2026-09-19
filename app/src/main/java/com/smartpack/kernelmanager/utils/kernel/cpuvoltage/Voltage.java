/*
 * Copyright (C) 2015-2016 Willi Ye <williye97@gmail.com>
 *
 * This file is part of Kernel Adiutor.
 *
 * Kernel Adiutor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Kernel Adiutor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Kernel Adiutor. If not, see <http://www.gnu.org/licenses/>.
 */
package com.smartpack.kernelmanager.utils.kernel.cpuvoltage;

import android.content.Context;

import com.smartpack.kernelmanager.fragments.ApplyOnBootFragment;
import com.smartpack.kernelmanager.utils.Utils;
import com.smartpack.kernelmanager.utils.root.Control;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class Voltage {

    private static Voltage sIOInstance;

    public static Voltage getInstance() {
        if (sIOInstance == null) {
            sIOInstance = new Voltage();
        }
        return sIOInstance;
    }

    private static final String CPU_OVERRIDE_VMIN =
            "/sys/devices/system/cpu/cpu0/cpufreq/override_vmin";

    private static final String CPU_VOLTAGE_CLUSTER0 =
            "/sys/devices/system/cpu/cpu0/cpufreq/UV_mV_table";
    private static final String CPU_VOLTAGE_CLUSTER1 =
            "/sys/devices/system/cpu/cpu4/cpufreq/UV_mV_table";

    private static final String CPU_VDD_VOLTAGE =
            "/sys/devices/system/cpu/cpu0/cpufreq/vdd_levels";
    private static final String CPU_FAUX_VOLTAGE =
            "/sys/devices/system/cpu/cpufreq/vdd_table/vdd_levels";

    private final HashMap<String, Boolean> mVoltages = new HashMap<>();
    private final HashMap<String, Integer> mOffset = new HashMap<>();
    private final HashMap<String, String> mSplitNewline = new HashMap<>();
    private final HashMap<String, String> mSplitLine = new HashMap<>();
    private final HashMap<String, Boolean> mAppend = new HashMap<>();

    private int mCluster = 0;
    private String PATH;

    {
        mVoltages.put(CPU_VOLTAGE_CLUSTER0, false);
        mVoltages.put(CPU_VOLTAGE_CLUSTER1, false);
        mVoltages.put(CPU_VDD_VOLTAGE, true);
        mVoltages.put(CPU_FAUX_VOLTAGE, true);

        mOffset.put(CPU_VOLTAGE_CLUSTER0, 1);
        mOffset.put(CPU_VOLTAGE_CLUSTER1, 1);
        mOffset.put(CPU_VDD_VOLTAGE, 1);
        mOffset.put(CPU_FAUX_VOLTAGE, 1000);

        mSplitNewline.put(CPU_VDD_VOLTAGE, "\\r?\\n");
        mSplitNewline.put(CPU_FAUX_VOLTAGE, "\\r?\\n");

        mSplitLine.put(CPU_VDD_VOLTAGE, ":");
        mSplitLine.put(CPU_FAUX_VOLTAGE, ":");

        mAppend.put(CPU_VOLTAGE_CLUSTER0, true);
        mAppend.put(CPU_VOLTAGE_CLUSTER1, true);
        mAppend.put(CPU_VDD_VOLTAGE, false);
        mAppend.put(CPU_FAUX_VOLTAGE, false);

        updatePath();
    }

    private Voltage() {
    }

    private void updatePath() {
        String clusterPath = getClusterPath(mCluster);

        if (Utils.existFile(clusterPath)) {
            PATH = clusterPath;
            return;
        }

        if (PATH == null || !Utils.existFile(PATH)) {
            PATH = null;

            if (Utils.existFile(CPU_VDD_VOLTAGE)) {
                PATH = CPU_VDD_VOLTAGE;
            } else if (Utils.existFile(CPU_FAUX_VOLTAGE)) {
                PATH = CPU_FAUX_VOLTAGE;
            }
        }
    }

    private String getClusterPath(int cluster) {
        return cluster == 1 ? CPU_VOLTAGE_CLUSTER1 : CPU_VOLTAGE_CLUSTER0;
    }

    public List<String> getClusters() {
        List<String> clusters = new ArrayList<>();

        if (Utils.existFile(CPU_VOLTAGE_CLUSTER0)) {
            clusters.add("CPU0-3");
        }

        if (Utils.existFile(CPU_VOLTAGE_CLUSTER1)) {
            clusters.add("CPU4-7");
        }

        return clusters;
    }

    public int getCluster() {
        return mCluster;
    }

    public void setCluster(int cluster) {
        if (cluster < 0 || cluster > 1) {
            return;
        }

        String path = getClusterPath(cluster);
        if (!Utils.existFile(path)) {
            return;
        }

        mCluster = cluster;
        PATH = path;
    }

    public void setGlobalOffset(int adjust, Context context) {
        if (PATH == null) {
            return;
        }

        List<String> voltages = getVoltages();
        if (voltages == null) {
            return;
        }

        StringBuilder value = new StringBuilder();

        if (isUVTable()) {
            for (String volt : voltages) {
                if (value.length() > 0) {
                    value.append(" ");
                }
                value.append(Utils.strToInt(volt) + adjust);
            }
        } else if (mAppend.get(PATH)) {
            for (String volt : voltages) {
                if (value.length() > 0) {
                    value.append(" ");
                }
                value.append(Utils.strToInt(volt) + adjust);
            }
        } else {
            value = new StringBuilder(
                    String.valueOf(adjust * mOffset.get(PATH)));

            if (adjust > 0) {
                value.insert(0, "+");
            }
        }

        run(Control.write(value.toString(), PATH), PATH, context);
    }

    public void setVoltage(String freq, String voltage, Context context) {
        if (PATH == null) {
            return;
        }

        List<String> freqs = getFreqs();
        List<String> voltages = getVoltages();

        if (freqs == null || voltages == null) {
            return;
        }

        int position = freqs.indexOf(freq);
        if (position < 0) {
            return;
        }

        if (isUVTable() || mAppend.get(PATH)) {
            if (position >= voltages.size()) {
                return;
            }

            StringBuilder command = new StringBuilder();

            for (int i = 0; i < voltages.size(); i++) {
                String value = i == position ? voltage : voltages.get(i);

                if (command.length() > 0) {
                    command.append(" ");
                }

                command.append(value);
            }

            run(Control.write(command.toString(), PATH), PATH, context);
        } else {
            run(Control.write(freq + " "
                    + Utils.strToInt(voltage) * mOffset.get(PATH),
                    PATH + freq, context);
        }
    }

    public List<String> getVoltages() {
        if (PATH == null || !Utils.existFile(PATH)) {
            return null;
        }

        String value = Utils.readFile(PATH);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        List<String> voltages = new ArrayList<>();

        if (isUVTable()) {
            String[] lines = value.trim().split("\\r?\\n");

            for (String line : lines) {
                String[] parts = line.trim().split("\\s+");

                if (parts.length < 2) {
                    continue;
                }

                voltages.add(parts[1].trim());
            }

            return voltages.isEmpty() ? null : voltages;
        }

        String[] lines = value.trim().split(
                Objects.requireNonNull(mSplitNewline.get(PATH)));

        for (String line : lines) {
            String[] voltageLine = line.split(
                    Objects.requireNonNull(mSplitLine.get(PATH)));

            if (voltageLine.length > 1) {
                voltages.add(String.valueOf(
                        Utils.strToInt(voltageLine[1].trim())
                                / mOffset.get(PATH)));
            }
        }

        return voltages.isEmpty() ? null : voltages;
    }

    public List<String> getFreqs() {
        if (PATH == null || !Utils.existFile(PATH)) {
            return null;
        }

        String value = Utils.readFile(PATH);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        List<String> freqs = new ArrayList<>();

        if (isUVTable()) {
            String[] lines = value.trim().split("\\r?\\n");

            for (String line : lines) {
                String[] parts = line.trim().split("\\s+");

                if (parts.length < 2) {
                    continue;
                }

                String freq = parts[0].trim();

                if (freq.startsWith("mhz:")) {
                    freq = freq.substring(4);
                }

                freqs.add(freq);
            }

            return freqs.isEmpty() ? null : freqs;
        }

        String[] lines = value.trim().split(
                Objects.requireNonNull(mSplitNewline.get(PATH)));

        for (String line : lines) {
            String[] voltageLine = line.split(
                    Objects.requireNonNull(mSplitLine.get(PATH)));

            if (voltageLine.length > 0) {
                freqs.add(voltageLine[0].trim());
            }
        }

        return freqs.isEmpty() ? null : freqs;
    }

    public boolean isVddVoltage() {
        return PATH != null && mVoltages.get(PATH);
    }

    private boolean isUVTable() {
        return CPU_VOLTAGE_CLUSTER0.equals(PATH)
                || CPU_VOLTAGE_CLUSTER1.equals(PATH);
    }

    public void enableOverrideVmin(boolean enable, Context context) {
        run(Control.write(enable ? "1" : "0", CPU_OVERRIDE_VMIN),
                CPU_OVERRIDE_VMIN, context);
    }

    public boolean isOverrideVminEnabled() {
        return Utils.readFile(CPU_OVERRIDE_VMIN).equals("1");
    }

    public boolean hasOverrideVmin() {
        return Utils.existFile(CPU_OVERRIDE_VMIN);
    }

    public boolean supported() {
        return PATH != null;
    }

    private void run(String command, String id, Context context) {
        Control.runSetting(command, ApplyOnBootFragment.CPU_VOLTAGE,
                id, context);
    }
}
