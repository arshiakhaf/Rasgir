package ir.rasgir.test

import java.io.File

fun main(args: Array<String>) {
    val reportPath = args.firstOrNull() ?: "test-report.txt"
    Harness.verbose = args.any { it == "v" }
    Harness.runToReport(File(reportPath)) {
        println("گزارش تست‌های واحد — رأس‌گیر چک (نسخه JVM)")
        println("تاریخ اجرا: ${java.time.LocalDateTime.now()}")
        RationalTest.run()
        JalaliMoneyTest.run()
        EngineTest.run()
        ModelTest.run()
        LicenseTest.run()
        if (Harness.failures.isNotEmpty()) {
            println("\n--- فهرست خطاها ---")
            Harness.failures.forEach { println(it) }
        }
        Harness.summary()
    }
}
