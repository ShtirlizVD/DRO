package com.dro.lathe;

import java.util.ArrayList;
import java.util.List;

/**
 * Калькулятор проходов для нарезания резьбы
 * Основано на методике Sandvik Coromant
 */
public class ThreadPassCalculator {

    /**
     * Данные одного прохода
     */
    public static class PassInfo {
        public int passNumber;           // Номер прохода
        public double depthIncrement;    // Приращение глубины на этом проходе (мм)
        public double totalDepth;        // Суммарная глубина после прохода (мм)
        public double diameterTarget;    // Целевой диаметр (мм)
        public boolean isCompleted;      // Пройден ли этот шаг

        public PassInfo(int passNumber, double depthIncrement, double totalDepth,
                        double diameterTarget, boolean isCompleted) {
            this.passNumber = passNumber;
            this.depthIncrement = depthIncrement;
            this.totalDepth = totalDepth;
            this.diameterTarget = diameterTarget;
            this.isCompleted = isCompleted;
        }
    }

    /**
     * Результат расчёта проходов
     */
    public static class CalculationResult {
        public ThreadDatabase.ThreadInfo thread;
        public ThreadDatabase.ThreadType type;
        public List<PassInfo> passes;
        public double startDiameter;
        public double finalDiameter;
        public double totalDepth;
        public int completedPasses;
        public int totalPasses;
        public double completedDepth;

        public boolean isComplete() {
            return completedPasses >= totalPasses;
        }

        public double getProgressPercent() {
            if (totalDepth == 0) return 0;
            return (completedDepth / totalDepth) * 100;
        }
    }

    /**
     * Метод врезания
     */
    public enum InfeedMethod {
        RADIAL,       // Радиальное врезание (прямой)
        FLANK,        // Боковое врезание (под углом профиля)
        MODIFIED      // Модифицированное (чередование)
    }

    /**
     * Рассчитать проходы для резьбы
     *
     * @param thread Информация о резьбе
     * @param type   Тип (наружная/внутренняя)
     * @param method Метод врезания
     * @return Результат расчёта
     */
    public static CalculationResult calculate(ThreadDatabase.ThreadInfo thread,
                                               ThreadDatabase.ThreadType type,
                                               InfeedMethod method) {
        return calculate(thread, type, method, new boolean[0]);
    }

    /**
     * Рассчитать проходы с учётом уже пройденных
     *
     * @param thread          Информация о резьбе
     * @param type            Тип (наружная/внутренняя)
     * @param method          Метод врезания
     * @param completedPasses Массив флагов пройденных проходов
     * @return Результат расчёта
     */
    public static CalculationResult calculate(ThreadDatabase.ThreadInfo thread,
                                               ThreadDatabase.ThreadType type,
                                               InfeedMethod method,
                                               boolean[] completedPasses) {
        CalculationResult result = new CalculationResult();
        result.thread = thread;
        result.type = type;
        result.startDiameter = thread.getStartDiameter(type);
        result.finalDiameter = thread.getFinalDiameter(type);
        result.totalDepth = thread.getCuttingDepth(type);
        result.passes = new ArrayList<>();

        // Расчёт количества проходов и глубин
        // Формула Sandvik: количество проходов зависит от шага
        // Для метрической резьбы: nap ≈ 10 + P*5 (где P в мм)
        // Минимум 3 прохода, максимум зависит от глубины

        int numberOfPasses = calculateNumberOfPasses(thread.pitch, result.totalDepth);

        // Генерация глубин проходов
        // Метод: первый проход ~0.1-0.2мм, затем постепенно уменьшаем приращение
        // Это стандартная практика для резьбовых пластин

        double[] depths = generatePassDepths(numberOfPasses, result.totalDepth, method);

        // Создаём список проходов
        double cumulativeDepth = 0;
        for (int i = 0; i < numberOfPasses; i++) {
            cumulativeDepth += depths[i];

            // Целевой диаметр зависит от типа резьбы
            double targetDiameter;
            if (type == ThreadDatabase.ThreadType.EXTERNAL) {
                // Наружная: от наружного диаметра вглубь
                targetDiameter = result.startDiameter - 2 * cumulativeDepth;
            } else {
                // Внутренняя: от отверстия вглубь (диаметр увеличивается)
                targetDiameter = result.startDiameter + 2 * cumulativeDepth;
            }

            boolean isCompleted = (i < completedPasses.length && completedPasses[i]);

            PassInfo pass = new PassInfo(
                    i + 1,
                    depths[i],
                    cumulativeDepth,
                    targetDiameter,
                    isCompleted
            );

            result.passes.add(pass);

            if (isCompleted) {
                result.completedPasses++;
                result.completedDepth = cumulativeDepth;
            }
        }

        result.totalPasses = numberOfPasses;

        // Добавляем spring passes (холостые проходы для калибровки)
        // Обычно 2-3 прохода на финальной глубине
        int springPasses = calculateSpringPasses(thread.pitch);
        for (int i = 0; i < springPasses; i++) {
            PassInfo pass = new PassInfo(
                    numberOfPasses + i + 1,
                    0,  // Глубина не увеличивается
                    result.totalDepth,
                    result.finalDiameter,
                    false
            );
            result.passes.add(pass);
            result.totalPasses++;
        }

        return result;
    }

    /**
     * Рассчитать количество проходов
     */
    private static int calculateNumberOfPasses(double pitch, double totalDepth) {
        // Эмпирическая формула на основе практики
        // Для стандартных резьбовых пластин

        if (pitch <= 0.5) {
            return 3;  // M3 и менее
        } else if (pitch <= 0.7) {
            return 5;  // M4
        } else if (pitch <= 0.8) {
            return 6;  // M5
        } else if (pitch <= 1.0) {
            return 7;  // M6
        } else if (pitch <= 1.25) {
            return 9;  // M8
        } else if (pitch <= 1.5) {
            return 11; // M10
        } else if (pitch <= 1.75) {
            return 13; // M12
        } else if (pitch <= 2.0) {
            return 15; // M14, M16
        } else if (pitch <= 2.5) {
            return 18; // M18, M20, M22
        } else if (pitch <= 3.0) {
            return 21; // M24
        } else if (pitch <= 3.5) {
            return 24; // M30, M33
        } else if (pitch <= 4.0) {
            return 27; // M36, M39
        } else {
            return 30; // M42 и более
        }
    }

    /**
     * Генерировать глубины проходов
     * Используется метод уменьшающегося приращения
     */
    private static double[] generatePassDepths(int numberOfPasses, double totalDepth,
                                                 InfeedMethod method) {
        double[] depths = new double[numberOfPasses];

        // Метод постоянного уменьшения (Standard)
        // Первый проход: ~0.1-0.15 мм
        // Последующие уменьшаются пропорционально

        double firstPassDepth = Math.min(0.15, totalDepth / 3);
        double lastPassDepth = 0.03; // Минимальная глубина последнего прохода

        // Формула: глубина прохода уменьшается по закону
        // ap(n) = ap(1) * sqrt(n-1) / sqrt(n)
        // Сумма всех проходов = totalDepth

        double sumCoefficients = 0;
        for (int i = 1; i <= numberOfPasses; i++) {
            // Коэффициент уменьшается с каждым проходом
            double coeff = 1.0 / Math.sqrt(i);
            sumCoefficients += coeff;
        }

        double baseDepth = totalDepth / sumCoefficients;

        for (int i = 0; i < numberOfPasses; i++) {
            double coeff = 1.0 / Math.sqrt(i + 1);
            depths[i] = baseDepth * coeff;

            // Ограничения
            if (i == 0) {
                depths[i] = Math.max(depths[i], firstPassDepth);
            }
            if (i == numberOfPasses - 1) {
                depths[i] = Math.max(depths[i], lastPassDepth);
            }
        }

        // Корректировка для точного достижения глубины
        double actualSum = 0;
        for (double d : depths) actualSum += d;
        double correction = totalDepth / actualSum;
        for (int i = 0; i < numberOfPasses; i++) {
            depths[i] *= correction;
        }

        // Округление до разумной точности (0.001 мм)
        for (int i = 0; i < numberOfPasses; i++) {
            depths[i] = Math.round(depths[i] * 1000) / 1000.0;
        }

        return depths;
    }

    /**
     * Рассчитать количество холостых (калибровочных) проходов
     */
    private static int calculateSpringPasses(double pitch) {
        if (pitch <= 1.0) return 2;
        if (pitch <= 2.0) return 3;
        return 4;
    }

    /**
     * Получить рекомендации по подаче и скорости для резьбовой пластины
     */
    public static class CuttingParameters {
        public double cuttingSpeed;    // Скорость резания (м/мин)
        public double feedRate;        // Подача (мм/об) - равна шагу для резьбы
        public int depthOfCut;         // Рекомендуемая глубина резания

        public CuttingParameters(double cuttingSpeed, double feedRate, int depthOfCut) {
            this.cuttingSpeed = cuttingSpeed;
            this.feedRate = feedRate;
            this.depthOfCut = depthOfCut;
        }
    }

    /**
     * Получить рекомендуемые параметры резания
     * Зависят от материала и типа пластины
     */
    public static CuttingParameters getRecommendedParameters(double pitch, String material) {
        // Стандартные значения для стали
        double speed = 80; // м/мин

        if (material.contains("нержавеющая") || material.contains("stainless")) {
            speed = 60;
        } else if (material.contains("алюмин") || material.contains("aluminum")) {
            speed = 150;
        } else if (material.contains("латунь") || material.contains("brass")) {
            speed = 120;
        } else if (material.contains("чугун") || material.contains("cast")) {
            speed = 70;
        }

        return new CuttingParameters(speed, pitch, 0);
    }
}
