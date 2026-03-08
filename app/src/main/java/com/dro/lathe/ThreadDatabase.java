package com.dro.lathe;

import java.util.ArrayList;
import java.util.List;

/**
 * База данных резьб с полными параметрами по ГОСТ и ISO
 */
public class ThreadDatabase {

    /**
     * Тип резьбы (внутренняя/наружная)
     */
    public enum ThreadType {
        EXTERNAL,  // Наружная (болт)
        INTERNAL   // Внутренняя (гайка)
    }

    /**
     * Категория резьбы
     */
    public enum ThreadCategory {
        METRIC,    // Метрическая (M)
        INCH,      // Дюймовая (UNC/UNF)
        PIPE       // Трубная (G)
    }

    /**
     * Полные данные о резьбе
     */
    public static class ThreadInfo {
        public String name;           // Обозначение (M10, 1/2"-13, G1/2")
        public ThreadCategory category;
        public double nominalDiameter; // Номинальный диаметр (мм)
        public double pitch;          // Шаг (мм)
        public double threadsPerInch; // Число ниток на дюйм (для дюймовых)
        public double majorDiameter;  // Наружный диаметр (мм)
        public double minorDiameter;  // Внутренний диаметр наружной резьбы (мм)
        public double minorDiameterInternal; // Внутренний диаметр внутренней резьбы (мм)
        public double pitchDiameter;  // Средний диаметр (мм)
        public double threadDepth;    // Глубина резьбы (мм) H1 = 0.541266*P для 60°
        public double tapDrillSize;   // Диаметр отверстия под резьбу (мм)

        public ThreadInfo(String name, ThreadCategory category, double nominalDiameter,
                          double pitch, double threadsPerInch, double majorDiameter,
                          double minorDiameter, double minorDiameterInternal,
                          double pitchDiameter, double threadDepth, double tapDrillSize) {
            this.name = name;
            this.category = category;
            this.nominalDiameter = nominalDiameter;
            this.pitch = pitch;
            this.threadsPerInch = threadsPerInch;
            this.majorDiameter = majorDiameter;
            this.minorDiameter = minorDiameter;
            this.minorDiameterInternal = minorDiameterInternal;
            this.pitchDiameter = pitchDiameter;
            this.threadDepth = threadDepth;
            this.tapDrillSize = tapDrillSize;
        }

        /**
         * Получить стартовый диаметр для нарезания
         * Для наружной: наружный диаметр минус допуск
         * Для внутренней: диаметр отверстия
         */
        public double getStartDiameter(ThreadType type) {
            if (type == ThreadType.INTERNAL) {
                return tapDrillSize;
            } else {
                // Для наружной резьбы начинаем с номинального диаметра
                return majorDiameter;
            }
        }

        /**
         * Получить конечный диаметр
         */
        public double getFinalDiameter(ThreadType type) {
            if (type == ThreadType.INTERNAL) {
                return minorDiameterInternal;
            } else {
                return minorDiameter;
            }
        }

        /**
         * Глубина резания (радиальная)
         */
        public double getCuttingDepth(ThreadType type) {
            if (type == ThreadType.INTERNAL) {
                // Для внутренней: от отверстия до внутреннего диаметра резьбы
                return (minorDiameterInternal - tapDrillSize) / 2;
            } else {
                // Для наружной: от наружного до внутреннего диаметра
                return (majorDiameter - minorDiameter) / 2;
            }
        }
    }

    // ==================== МЕТРИЧЕСКИЕ РЕЗЬБЫ (ГОСТ 24705-2004) ====================
    // Формулы: H = 0.866025*P, H1 = 0.541266*P
    // d2 = d - 0.649519*P (средний диаметр)
    // d1 = d - 1.082532*P (внутренний диаметр)
    // D1 = d - 1.082532*P (внутренний диаметр гайки)
    // Отверстие под резьбу ≈ d - P

    private static final ThreadInfo[] METRIC_THREADS = {
            // Мелкие резьбы
            new ThreadInfo("M3", ThreadCategory.METRIC, 3.0, 0.5, 0,
                    3.000, 2.387, 2.459, 2.675, 0.307, 2.5),
            new ThreadInfo("M4", ThreadCategory.METRIC, 4.0, 0.7, 0,
                    4.000, 3.141, 3.242, 3.545, 0.429, 3.3),
            new ThreadInfo("M5", ThreadCategory.METRIC, 5.0, 0.8, 0,
                    5.000, 4.019, 4.134, 4.480, 0.491, 4.2),
            new ThreadInfo("M6", ThreadCategory.METRIC, 6.0, 1.0, 0,
                    6.000, 4.773, 4.917, 5.350, 0.613, 5.0),
            new ThreadInfo("M8", ThreadCategory.METRIC, 8.0, 1.25, 0,
                    8.000, 6.466, 6.647, 7.188, 0.767, 6.8),
            new ThreadInfo("M10", ThreadCategory.METRIC, 10.0, 1.5, 0,
                    10.000, 8.160, 8.376, 9.026, 0.920, 8.5),
            new ThreadInfo("M10x1.25", ThreadCategory.METRIC, 10.0, 1.25, 0,
                    10.000, 8.466, 8.647, 9.188, 0.767, 8.8),
            new ThreadInfo("M12", ThreadCategory.METRIC, 12.0, 1.75, 0,
                    12.000, 9.853, 10.106, 10.863, 1.073, 10.2),
            new ThreadInfo("M12x1.5", ThreadCategory.METRIC, 12.0, 1.5, 0,
                    12.000, 10.160, 10.376, 11.026, 0.920, 10.5),
            new ThreadInfo("M14", ThreadCategory.METRIC, 14.0, 2.0, 0,
                    14.000, 11.546, 11.835, 12.701, 1.227, 12.0),
            new ThreadInfo("M14x1.5", ThreadCategory.METRIC, 14.0, 1.5, 0,
                    14.000, 12.160, 12.376, 13.026, 0.920, 12.5),
            new ThreadInfo("M16", ThreadCategory.METRIC, 16.0, 2.0, 0,
                    16.000, 13.546, 13.835, 14.701, 1.227, 14.0),
            new ThreadInfo("M16x1.5", ThreadCategory.METRIC, 16.0, 1.5, 0,
                    16.000, 14.160, 14.376, 15.026, 0.920, 14.5),
            new ThreadInfo("M18", ThreadCategory.METRIC, 18.0, 2.5, 0,
                    18.000, 14.933, 15.294, 16.376, 1.533, 15.5),
            new ThreadInfo("M18x1.5", ThreadCategory.METRIC, 18.0, 1.5, 0,
                    18.000, 16.160, 16.376, 17.026, 0.920, 16.5),
            new ThreadInfo("M20", ThreadCategory.METRIC, 20.0, 2.5, 0,
                    20.000, 16.933, 17.294, 18.376, 1.533, 17.5),
            new ThreadInfo("M20x1.5", ThreadCategory.METRIC, 20.0, 1.5, 0,
                    20.000, 18.160, 18.376, 19.026, 0.920, 18.5),
            new ThreadInfo("M22", ThreadCategory.METRIC, 22.0, 2.5, 0,
                    22.000, 18.933, 19.294, 20.376, 1.533, 19.5),
            new ThreadInfo("M22x1.5", ThreadCategory.METRIC, 22.0, 1.5, 0,
                    22.000, 20.160, 20.376, 21.026, 0.920, 20.5),
            new ThreadInfo("M24", ThreadCategory.METRIC, 24.0, 3.0, 0,
                    24.000, 20.319, 20.752, 22.051, 1.840, 21.0),
            new ThreadInfo("M24x2", ThreadCategory.METRIC, 24.0, 2.0, 0,
                    24.000, 21.546, 21.835, 22.701, 1.227, 22.0),
            new ThreadInfo("M27", ThreadCategory.METRIC, 27.0, 3.0, 0,
                    27.000, 23.319, 23.752, 25.051, 1.840, 24.0),
            new ThreadInfo("M30", ThreadCategory.METRIC, 30.0, 3.5, 0,
                    30.000, 25.706, 26.211, 27.727, 2.147, 26.5),
            new ThreadInfo("M33", ThreadCategory.METRIC, 33.0, 3.5, 0,
                    33.000, 28.706, 29.211, 30.727, 2.147, 29.5),
            new ThreadInfo("M36", ThreadCategory.METRIC, 36.0, 4.0, 0,
                    36.000, 31.093, 31.670, 33.402, 2.454, 32.0),
            new ThreadInfo("M39", ThreadCategory.METRIC, 39.0, 4.0, 0,
                    39.000, 34.093, 34.670, 36.402, 2.454, 35.0),
            new ThreadInfo("M42", ThreadCategory.METRIC, 42.0, 4.5, 0,
                    42.000, 36.479, 37.129, 39.077, 2.760, 37.5),
            new ThreadInfo("M45", ThreadCategory.METRIC, 45.0, 4.5, 0,
                    45.000, 39.479, 40.129, 42.077, 2.760, 40.5),
            new ThreadInfo("M48", ThreadCategory.METRIC, 48.0, 5.0, 0,
                    48.000, 41.866, 42.587, 44.752, 3.067, 43.0),
    };

    // ==================== ДЮЙМОВЫЕ РЕЗЬБЫ (UNC/UNF) ====================
    // UNC - Unified National Coarse
    // UNF - Unified National Fine
    // Угол профиля 60°, формула глубины: H1 = 0.61343*P

    private static final ThreadInfo[] INCH_THREADS = {
            // UNC (крупная)
            new ThreadInfo("1/4\"-20 UNC", ThreadCategory.INCH, 6.35, 1.270, 20,
                    6.350, 4.976, 5.175, 5.537, 0.687, 5.1),
            new ThreadInfo("5/16\"-18 UNC", ThreadCategory.INCH, 7.94, 1.411, 18,
                    7.938, 6.311, 6.532, 6.934, 0.764, 6.5),
            new ThreadInfo("3/8\"-16 UNC", ThreadCategory.INCH, 9.53, 1.588, 16,
                    9.525, 7.638, 7.874, 8.334, 0.859, 7.9),
            new ThreadInfo("7/16\"-14 UNC", ThreadCategory.INCH, 11.11, 1.814, 14,
                    11.112, 8.944, 9.214, 9.738, 0.982, 9.2),
            new ThreadInfo("1/2\"-13 UNC", ThreadCategory.INCH, 12.70, 1.954, 13,
                    12.700, 10.294, 10.584, 11.158, 1.058, 10.6),
            new ThreadInfo("9/16\"-12 UNC", ThreadCategory.INCH, 14.29, 2.117, 12,
                    14.288, 11.620, 11.938, 12.573, 1.146, 11.9),
            new ThreadInfo("5/8\"-11 UNC", ThreadCategory.INCH, 15.88, 2.309, 11,
                    15.875, 12.913, 13.264, 13.972, 1.250, 13.2),
            new ThreadInfo("3/4\"-10 UNC", ThreadCategory.INCH, 19.05, 2.540, 10,
                    19.050, 15.710, 16.097, 16.876, 1.375, 16.0),
            new ThreadInfo("7/8\"-9 UNC", ThreadCategory.INCH, 22.23, 2.822, 9,
                    22.225, 18.461, 18.885, 19.748, 1.528, 18.8),
            new ThreadInfo("1\"-8 UNC", ThreadCategory.INCH, 25.40, 3.175, 8,
                    25.400, 21.183, 21.647, 22.663, 1.719, 21.6),
            new ThreadInfo("1 1/8\"-7 UNC", ThreadCategory.INCH, 28.58, 3.629, 7,
                    28.575, 23.671, 24.176, 25.390, 1.964, 24.0),
            new ThreadInfo("1 1/4\"-7 UNC", ThreadCategory.INCH, 31.75, 3.629, 7,
                    31.750, 26.846, 27.351, 28.565, 1.964, 27.2),
            new ThreadInfo("1 3/8\"-6 UNC", ThreadCategory.INCH, 34.93, 4.233, 6,
                    34.925, 29.179, 29.754, 31.193, 2.291, 29.6),
            new ThreadInfo("1 1/2\"-6 UNC", ThreadCategory.INCH, 38.10, 4.233, 6,
                    38.100, 32.354, 32.929, 34.368, 2.291, 32.8),
            // UNF (мелкая)
            new ThreadInfo("1/4\"-28 UNF", ThreadCategory.INCH, 6.35, 0.907, 28,
                    6.350, 5.414, 5.524, 5.801, 0.491, 5.5),
            new ThreadInfo("5/16\"-24 UNF", ThreadCategory.INCH, 7.94, 1.058, 24,
                    7.938, 6.804, 6.932, 7.242, 0.572, 6.9),
            new ThreadInfo("3/8\"-24 UNF", ThreadCategory.INCH, 9.53, 1.058, 24,
                    9.525, 8.391, 8.519, 8.829, 0.572, 8.5),
            new ThreadInfo("7/16\"-20 UNF", ThreadCategory.INCH, 11.11, 1.270, 20,
                    11.112, 9.738, 9.937, 10.299, 0.687, 9.9),
            new ThreadInfo("1/2\"-20 UNF", ThreadCategory.INCH, 12.70, 1.270, 20,
                    12.700, 11.326, 11.525, 11.887, 0.687, 11.5),
            new ThreadInfo("9/16\"-18 UNF", ThreadCategory.INCH, 14.29, 1.411, 18,
                    14.288, 12.661, 12.882, 13.284, 0.764, 12.8),
            new ThreadInfo("5/8\"-18 UNF", ThreadCategory.INCH, 15.88, 1.411, 18,
                    15.875, 14.248, 14.469, 14.871, 0.764, 14.5),
            new ThreadInfo("3/4\"-16 UNF", ThreadCategory.INCH, 19.05, 1.588, 16,
                    19.050, 17.163, 17.399, 17.859, 0.859, 17.4),
            new ThreadInfo("7/8\"-14 UNF", ThreadCategory.INCH, 22.23, 1.814, 14,
                    22.225, 20.057, 20.327, 20.851, 0.982, 20.3),
            new ThreadInfo("1\"-12 UNF", ThreadCategory.INCH, 25.40, 2.117, 12,
                    25.400, 22.732, 23.050, 23.685, 1.146, 23.0),
    };

    // ==================== ТРУБНЫЕ РЕЗЬБЫ (G - BSP) ====================
    // Угол профиля 55°, теоретическая высота H = 0.960491*P
    // Рабочая высота h = 0.640327*P
    // Радиус закругления r = 0.137329*P

    private static final ThreadInfo[] PIPE_THREADS = {
            new ThreadInfo("G1/8\"", ThreadCategory.PIPE, 9.73, 0.907, 28,
                    9.728, 8.566, 8.696, 9.147, 0.581, 8.7),
            new ThreadInfo("G1/4\"", ThreadCategory.PIPE, 13.16, 1.337, 19,
                    13.157, 11.445, 11.634, 12.302, 0.856, 11.6),
            new ThreadInfo("G3/8\"", ThreadCategory.PIPE, 16.66, 1.337, 19,
                    16.662, 14.950, 15.139, 15.807, 0.856, 15.2),
            new ThreadInfo("G1/2\"", ThreadCategory.PIPE, 20.96, 1.814, 14,
                    20.955, 18.632, 18.888, 19.794, 1.162, 18.9),
            new ThreadInfo("G5/8\"", ThreadCategory.PIPE, 22.91, 1.814, 14,
                    22.911, 20.588, 20.844, 21.750, 1.162, 20.9),
            new ThreadInfo("G3/4\"", ThreadCategory.PIPE, 26.44, 1.814, 14,
                    26.441, 24.118, 24.374, 25.280, 1.162, 24.4),
            new ThreadInfo("G7/8\"", ThreadCategory.PIPE, 30.20, 1.814, 14,
                    30.201, 27.878, 28.134, 29.040, 1.162, 28.2),
            new ThreadInfo("G1\"", ThreadCategory.PIPE, 33.25, 2.309, 11,
                    33.249, 30.292, 30.620, 31.771, 1.479, 30.5),
            new ThreadInfo("G1 1/8\"", ThreadCategory.PIPE, 37.90, 2.309, 11,
                    37.897, 34.940, 35.268, 36.419, 1.479, 35.2),
            new ThreadInfo("G1 1/4\"", ThreadCategory.PIPE, 41.91, 2.309, 11,
                    41.910, 38.953, 39.281, 40.432, 1.479, 39.3),
            new ThreadInfo("G1 3/8\"", ThreadCategory.PIPE, 44.32, 2.309, 11,
                    44.323, 41.366, 41.694, 42.845, 1.479, 41.7),
            new ThreadInfo("G1 1/2\"", ThreadCategory.PIPE, 47.80, 2.309, 11,
                    47.803, 44.846, 45.174, 46.325, 1.479, 45.2),
            new ThreadInfo("G1 3/4\"", ThreadCategory.PIPE, 53.75, 2.309, 11,
                    53.746, 50.789, 51.117, 52.268, 1.479, 51.1),
            new ThreadInfo("G2\"", ThreadCategory.PIPE, 59.61, 2.309, 11,
                    59.614, 56.657, 56.985, 58.136, 1.479, 57.0),
    };

    /**
     * Получить все резьбы указанной категории
     */
    public static ThreadInfo[] getThreads(ThreadCategory category) {
        switch (category) {
            case METRIC:
                return METRIC_THREADS;
            case INCH:
                return INCH_THREADS;
            case PIPE:
                return PIPE_THREADS;
            default:
                return METRIC_THREADS;
        }
    }

    /**
     * Получить все резьбы всех категорий
     */
    public static List<ThreadInfo> getAllThreads() {
        List<ThreadInfo> all = new ArrayList<>();
        for (ThreadInfo t : METRIC_THREADS) all.add(t);
        for (ThreadInfo t : INCH_THREADS) all.add(t);
        for (ThreadInfo t : PIPE_THREADS) all.add(t);
        return all;
    }

    /**
     * Найти резьбу по имени
     */
    public static ThreadInfo findThread(String name) {
        for (ThreadInfo t : getAllThreads()) {
            if (t.name.equals(name)) return t;
        }
        return null;
    }
}
