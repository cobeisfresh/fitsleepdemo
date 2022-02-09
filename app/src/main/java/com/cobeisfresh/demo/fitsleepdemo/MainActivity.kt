package com.cobeisfresh.demo.fitsleepdemo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.format.DateUtils
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.SessionReadRequest
import com.google.android.material.snackbar.Snackbar
import java.lang.StringBuilder
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var account: GoogleSignInAccount
    private lateinit var fitnessOptions: FitnessOptions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestActivityRecognitionPermission()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSIONS_REQUEST_ACTIVITY_RECOGNITION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    createFitnessApiClient()
                } else {
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        "Revoked physical activity permission",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (resultCode) {
            Activity.RESULT_OK -> when (requestCode) {
                GOOGLE_FIT_PERMISSIONS_REQUEST_CODE -> {
                    accessGoogleFit()
                }
                else -> {
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        "Revoked Google Fit permission",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    @RequiresApi(29)
    private fun requestActivityRecognitionPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
            PERMISSIONS_REQUEST_ACTIVITY_RECOGNITION
        )
    }

    private fun createFitnessApiClient() {
        fitnessOptions = FitnessOptions.builder()
            .addDataType(DataType.TYPE_SLEEP_SEGMENT, FitnessOptions.ACCESS_READ)
            .build()
        account = GoogleSignIn.getAccountForExtension(this, fitnessOptions)
        if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
            GoogleSignIn.requestPermissions(
                this,
                GOOGLE_FIT_PERMISSIONS_REQUEST_CODE,
                account,
                fitnessOptions
            )
        } else {
            accessGoogleFit()
        }
    }

    private fun accessGoogleFit() {
        val end = ZonedDateTime.now()
        val start = end.minusWeeks(2)
        val endSeconds = end.toEpochSecond()
        val startSeconds = start.toEpochSecond()

        val request = SessionReadRequest.Builder()
            .readSessionsFromAllApps()
            .includeSleepSessions()
            .read(DataType.TYPE_SLEEP_SEGMENT)
            .setTimeInterval(startSeconds, endSeconds, TimeUnit.SECONDS)
            .build()

        val outputBuilder = StringBuilder()

        Fitness.getSessionsClient(this, account)
            .readSession(request)
            .addOnSuccessListener { response ->
                for (session in response.sessions) {
                    val sessionStart = DateUtils.formatDateTime(
                        this,
                        session.getStartTime(TimeUnit.MILLISECONDS),
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME
                    )
                    val sessionEnd = DateUtils.formatDateTime(
                        this,
                        session.getEndTime(TimeUnit.MILLISECONDS),
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME
                    )
                    outputBuilder.appendLine("Sleep between $sessionStart and $sessionEnd")

                    // If the sleep session has finer granularity sub-components, extract them:
                    val dataSets = response.getDataSet(session)
                    for (dataSet in dataSets) {
                        for (point in dataSet.dataPoints) {
                            val sleepStageVal = point.getValue(Field.FIELD_SLEEP_SEGMENT_TYPE).asInt()
                            val sleepStage = sleepStageNames[sleepStageVal]
                            val segmentStart = DateUtils.formatDateTime(
                                this,
                                point.getStartTime(TimeUnit.MILLISECONDS),
                                DateUtils.FORMAT_SHOW_TIME
                            )
                            val segmentEnd = DateUtils.formatDateTime(
                                this,
                                point.getEndTime(TimeUnit.MILLISECONDS),
                                DateUtils.FORMAT_SHOW_TIME
                            )
                            outputBuilder.appendLine("\t* $sleepStage \t $segmentStart - $segmentEnd")
                        }
                    }

                    findViewById<TextView>(R.id.tv_output).run {
                        typeface = Typeface.MONOSPACE
                        text = outputBuilder
                            .appendLine()
                            .toString()
                    }
                }
            }
    }

    companion object {
        const val PERMISSIONS_REQUEST_ACTIVITY_RECOGNITION = 2
        const val GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1
        const val TAG = "MainActivity"
        val sleepStageNames = arrayOf(
            "Unused     ",
            "Awake      ",
            "Sleep      ",
            "Out-of-bed ",
            "Light sleep",
            "Deep sleep ",
            "REM sleep  "
        )
    }
}