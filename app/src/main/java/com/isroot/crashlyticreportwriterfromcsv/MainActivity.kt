package com.isroot.crashlyticreportwriterfromcsv

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.google.gson.GsonBuilder
import com.isroot.crashlyticreportwriterfromcsv.ui.theme.Pink40
import com.opencsv.CSVReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.Calendar
import java.util.Locale

class MainActivity : ComponentActivity() {

    data class IssueLink(
        val issueId: String,
        val url: String,
        val title: String,
        val subTitle: String,
        val description: String,
        val jiraLink: String,
        val minVersion: String?,
        val latestVersion: String,
        val eventCountIn24: Int,
        val userCountIn24: Int,
        val eventCountIn90Days: Int,
        val userCountIn90Days: Int,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Pink40
                ) {
                    ReadCsvButton(btnName = "Read CSV", onClickBtn = {
                        readCsv()
                        readCsvToJson()
                    })
                }
            }
        }
    }

    private fun readCsvToJson() {
        lifecycleScope.launch(Dispatchers.IO) {
            val cal = Calendar.getInstance()
            val date = String.format(
                Locale.KOREA,
                "%04d_%02d_%02d",
                cal[Calendar.YEAR],
                cal[Calendar.MONTH] + 1,
                cal[Calendar.DAY_OF_MONTH]
            )

            val dir = File(filesDir.path + File.separatorChar + "Daily_Monitoring")
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val file = File(dir.path + File.separatorChar + date + ".json")
            try {
                val issueLinks = mutableListOf<IssueLink>()

                val inputStream = assets.open("new/AOS Crashlytics DB.csv")
                val csvReader = CSVReader(InputStreamReader(inputStream, "UTF-8"))
                val allContent = csvReader.readAll() as List<Array<String>>
                allContent.forEachIndexed lit@{ rowIndex, row ->

                    if (rowIndex == 0) {
                        // Log the column headers if needed
                        return@lit
                    } else {
                        val crashTitle = row[0] // Crash Event
                        if (crashTitle.isBlank()) return@lit

                        val url = getURLStr(row[1])
                        val issueId = getIssueId(url)

                        val content = row[2] // 내용
                        val userCount24 = row[3].toIntOrNull() ?: 0 // 24시간 사용자 수
                        val userEvent24 = row[4].toIntOrNull() ?: 0 // 24시간 이벤트 수
                        val userCount90 = row[5].toIntOrNull() ?: 0 // 90일간 사용자 수
                        val userEvent90 = row[6].toIntOrNull() ?: 0 // 90일간 이벤트 수
                        val versionToResolve = row[7] // 수정 예정 버전
                        val jira = row[8] // Jira

                        val issueLink = IssueLink(
                            issueId = issueId,
                            url = url,
                            title = crashTitle,
                            subTitle = "",
                            description = content,
                            jiraLink = if (jira.isNotBlank()) "https://balso.atlassian.net/browse/$jira" else "",
                            minVersion = null,
                            latestVersion = versionToResolve,
                            eventCountIn24 = userEvent24,
                            userCountIn24 = userCount24,
                            eventCountIn90Days = userEvent90,
                            userCountIn90Days = userCount90
                        )

                        issueLinks.add(issueLink)
                    }
                }

                // Convert list of IssueLink to JSON
                val gson = GsonBuilder().setPrettyPrinting().create()
                val json = gson.toJson(issueLinks)

                // Write JSON to file
                FileOutputStream(file, true).use { fos ->
                    fos.write(json.toByteArray())
                }

                Log.d("syTest", "JSON saved to ${file.absolutePath}")

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getURLStr(origin: String): String {
        return origin.substringBefore("?")
    }

    private fun getIssueId(origin: String): String {
        // 정규 표현식 패턴 정의
        val regex = """/issues/([^?]+)""".toRegex()

        // 정규 표현식에 매치되는 부분 찾기
        val matchResult = regex.find(origin)

        // 매치 결과가 있으면 그룹을 반환
        return matchResult?.groups?.get(1)?.value ?: ""
    }

    private fun readCsv() {
        lifecycleScope.launch(Dispatchers.IO) {
//            throw InflateException("Bad notification(tag=null, id=1000)")

            val cal = Calendar.getInstance()
            val date = String.format(
                Locale.KOREA,
                "%04d_%02d_%02d",
                cal[Calendar.YEAR],
                cal[Calendar.MONTH] + 1,
                cal[Calendar.DAY_OF_MONTH]
            )


            val dir = File(filesDir.path + File.separatorChar + "Daily_Monitoring")
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val file = File(dir.path + File.separatorChar + date + ".txt")
            try {
                FileOutputStream(file, true).use { fos ->
                    if (file.parentFile?.exists() != true) {
                        file.parentFile?.mkdirs()
                    }
                    fos.write("신규 보고 건 (24시간 기준):\n".toByteArray())
                    convertData(fos, "new/AOS Crashlytics DB.csv")
                    fos.write("\n기존 보고 건 (24시간 기준):\n".toByteArray())
                    convertData(fos, "old/AOS Crashlytics DB.csv")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun convertData(fos: FileOutputStream, path: String) {
        val inputStream = assets.open(path)
        val csvReader = CSVReader(InputStreamReader(inputStream, "UTF-8"))
        val allContent = csvReader.readAll() as List<Array<String>>
        allContent.forEachIndexed lit@{ rowIndex, row ->

            if (rowIndex == 0) {
                for (value in row) {
                    val sb = StringBuilder()
                    sb.append(value)
                    Log.d("monitor", "[Column] $sb")
                }
                return@lit
            } else {

                val crashTitle = row[0] // Crash Event
                if (crashTitle.isBlank()) return@lit
                val content = row[1] // 내용
                val userCount24 = row[2] // 24시간 사용자 수
                val userEvent24 = row[3] // 24시간 이벤트 수
                val userCount90 = row[4] //90일간 사용자 수
                val userEvent90 = row[5] // 90일간 이벤트 수
                val versionToResolve = row[6] // 수정 예정 버전
                val jira = row[7] // Jira
                // 이슈 필드

                val sb = StringBuilder()
                sb.appendLine(
                    "$rowIndex.$crashTitle" + if (versionToResolve.isNotBlank()) {
                        " (v$versionToResolve)"
                    } else {
                        ""
                    }
                )

                if (jira.isNotBlank()) {
                    sb.appendLine("https://balso.atlassian.net/browse/$jira")
                } else {
                    if (content.contains('\n')) sb.appendLine("현상:\n$content")
                    else sb.appendLine("현상 : $content")
                }
                sb.append("사용자 : $userCount24, ")
                sb.appendLine("이벤트 : $userEvent24")
                sb.append("90일 기준 사용자 : $userCount90, ")
                sb.appendLine("이벤트 : $userEvent90")
                Log.d("monitor", "[$rowIndex] $sb")
                fos.write(sb.toString().toByteArray())
                fos.write("\r\n".toByteArray())
            }
        }
    }
}

@Composable
fun ReadCsvButton(btnName: String, onClickBtn: () -> Unit) {
    Row {
        Button(
            onClick = { onClickBtn.invoke() },
        ) {
            Text(btnName)
        }
    }
}