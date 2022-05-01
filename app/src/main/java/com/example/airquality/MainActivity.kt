package com.example.airquality

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.airquality.databinding.ActivityMainBinding
import android.Manifest
import android.location.Address
import android.location.Geocoder
import com.example.airquality.retrofit.AirQualityResponse
import com.example.airquality.retrofit.AirQualityService
import com.example.airquality.retrofit.RetrofitConnection
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.create
import java.io.IOException
import java.lang.IllegalArgumentException
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding

    // 런타임 권한 요청시 필요한 요청 코드
    private val PERMISSION_REQUEST_CODE = 100

    // 요청할 권한 목록
    var REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION)

    // 위치 서비스 요청시 필요한 런처
    lateinit var getGPSPermissionLauncher: ActivityResultLauncher<Intent>

    // 위도와 경도를 가져올 때 필요
    lateinit var locationProvider: LocationProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)       // 뷰바인딩 설정
        setContentView(binding.root)

        checkAllPermissions()       // 권한 확인
        updateUI()
        setRefreshButton()
    }

    private fun setRefreshButton() {
        binding.btnRefresh.setOnClickListener {
            updateUI()
        }
    }

    private fun updateUI() {
        locationProvider = LocationProvider(this@MainActivity)

        // 위도, 경도 정보
        val latitude: Double = locationProvider.getLocationLatitude()
        val longitude: Double = locationProvider.getLocationLongitude()

        if(latitude != 0.0 || longitude != 0.0) {

            // 현재 위치를 가져오고 UI 업데이트
            val address = getCurrentAddress(latitude, longitude)
            // 주소가 null이 아닐 경우 UI 업데이트
            address?.let {
                binding.tvLocationTitle.text = "${it.thoroughfare}"     // 역삼 1동
                binding.tvLocationSubtitle.text = "${it.countryName}${it.adminArea}" // 대한민국 서울특별시
            }

            // 현재 미세먼지 농도 가져오고 UI 업데이트
            getAirQualityData(latitude, longitude)

        } else {
            Toast.makeText(this@MainActivity,
            "위도, 경도 정보를 가져올 수 없었습니다. 새로고침을 눌러주세요.", Toast.LENGTH_LONG).show()
        }
    }

    // 레트로핏 클래스를 이용하여 미세먼지 오염 정보 가져옴
    private fun getAirQualityData(latitude: Double, longitude: Double) {
        // 레트로핏 객체를 이용해 AirQualityService 인터페이스 구현체를 가져올 수 있음음
       val retrofitAPI = RetrofitConnection.getInstance().create(AirQualityService::class.java)

        /*AirQualityService 인터페이스 객체를 이용하여 Call 객체를 만든 후 enqueue() 함수를 실행하여 서버에 API 요청
        레트로핏 모든 요청은 retrofit2.Call 객체로 이루어짐(execute(): 동기적, enqueue(): 비동기적)*/
        retrofitAPI.getAirQualityData(
            latitude.toString(),
            longitude.toString(),
            "4e25da54-4083-43f6-ba38-9d9086f59e6c"
        ).enqueue(object  : Callback<AirQualityResponse>{
            override fun onResponse(
                call: Call<AirQualityResponse>,
                response: Response<AirQualityResponse>
            ) {
                // 정상적인 Response가 왔다면 UI 업데이트
                if (response.isSuccessful) {
                    Toast.makeText(this@MainActivity, "최신 정보 업데이트 완료!",
                        Toast.LENGTH_SHORT).show()
                    // response.body()가 null이 아니면 updateAirUI()
                    response.body()?.let { updateAirUI(it) }
                    } else {
                        Toast.makeText(this@MainActivity, "업데이트에 실패하였습니다.",
                            Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<AirQualityResponse>, t: Throwable) {
                t.printStackTrace()
            }
        })
    }

    private fun updateAirUI(airQualityData: AirQualityResponse) {
        val pollutionData = airQualityData.data?.current?.pollution

        // 수치 지정(메인화면 가운데 숫자)
        if (pollutionData != null) {
            binding.tvCount.text = pollutionData.aqius.toString()
        }

        // 측정된 날짜 지정(ZonedDateTime 클래스를 이용하여 서울 시간대 적용)
        val dateTime =
            ZonedDateTime.parse(pollutionData?.ts).withZoneSameInstant(
                ZoneId.of("Asia/Seoul"))
                .toLocalDateTime()
        val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        binding.tvCheckTime.text = dateTime.format(dateFormatter).toString()

        if (pollutionData != null) {
            when (pollutionData.aqius){
                in 0..50 -> {
                    binding.tvTitle.text = "좋음"
                    binding.imgBg.setImageResource(R.drawable.bg_good)
                }
                in 51..150 -> {
                    binding.tvTitle.text = "보통"
                    binding.imgBg.setImageResource(R.drawable.bg_soso)
                }
                in 151..200 -> {
                    binding.tvTitle.text = "나쁨"
                    binding.imgBg.setImageResource(R.drawable.bg_bad)
                }
                else -> {
                    binding.tvTitle.text = "매우 나쁨"
                    binding.imgBg.setImageResource(R.drawable.bg_worst)
                }
            }
        }
    }

    fun getCurrentAddress(latitude: Double, longitude: Double) : Address? {
        val geocoder = Geocoder(this, Locale.getDefault())
        val addresses: List<Address>?

        addresses = try {
            // Geocoder 객체를 이용하여 위도와 경도로부터 리스트를 가져옴
            geocoder.getFromLocation(latitude, longitude, 7)
        } catch (ioException: IOException) {
            Toast.makeText(this, "지오코더 서비스 사용불가합니다.", Toast.LENGTH_LONG).show()
            return null
        } catch (illegalArgumentException: IllegalArgumentException) {
            Toast.makeText(this, "잘못된 위도, 경도 입니다.", Toast.LENGTH_LONG).show()
            return null
        }

        // 에러는 아니지만 주소가 발견되지 않은 경우
        if (addresses ==  null || addresses.size == 0) {
            Toast.makeText(this, "주소가 발견되지 않았습니다.", Toast.LENGTH_LONG).show()
            return null
        }

        val address: Address = addresses[0]
        return address
    }

    private fun checkAllPermissions() {
        // GPS가 켜져있는지 확인
        if (!isLocationServicesAvailable()) {
            showDialogForLocationServiceSetting();
        } else {        // 런타임 앱 권한이 모두 허용되어 있는지 확인
            isRunTimePermissionsGranted();
        }
    }

    fun isLocationServicesAvailable() : Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        return (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
    }

    fun isRunTimePermissionsGranted() {
        // 위치 퍼미션을 가지고 있는지 체크
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(
            this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION
        )
        val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(
            this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (hasFineLocationPermission != PackageManager.PERMISSION_GRANTED ||
                hasCoarseLocationPermission != PackageManager.PERMISSION_GRANTED) {
            // 권한이 한 개라도 없다면 퍼미션 요청
            ActivityCompat.requestPermissions(this@MainActivity,
            REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.size == REQUIRED_PERMISSIONS.size) {

            // 요청 코드가 PERMISSIONS_REQUEST_CODE이고, 요청한 퍼미션 개수만큼 수신 되었다면
            var checkResult = true

            // 모든 퍼미션을 허용했는지 체크
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    checkResult = false
                    break
                }
            }

            if (checkResult) {
                // 위치값을 가지고 올 수 있음
                updateUI()

            } else {
                // 퍼미션이 거부되었다면 앱 종료
                Toast.makeText(
                    this@MainActivity, "퍼미션이 거부되었습니다. 앱을 다시 실행하여 허용해주세요.",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    private fun showDialogForLocationServiceSetting() {
        // ActivityResultLauncher 설정(결과값 을 반환해야 하는 인텐트를 실행할 수 있음)
        getGPSPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) { result ->
            // 결과 값을 받았을 때
            if (result.resultCode == Activity.RESULT_OK) {
                // 사용자가 GPS를 활성화 시켰는지 확인
                if(isLocationServicesAvailable()) {
                    isRunTimePermissionsGranted()       // 런타임 권한 확인
                } else {
                    // 위치 서비스가 허용되지 않았다면 앱 종료
                    Toast.makeText(this@MainActivity, "위치 서비스를 사용할 수 없습니다.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()        // 액티비티 종료
                }
            }
        }

        val builder : AlertDialog.Builder = AlertDialog.Builder(this@MainActivity)

        builder.setTitle("위치 서비스 비활성화")
        builder.setMessage("위치 서비스가 꺼져 있습니다. 설정해야 앱을 사용할 수 있습니다.")
        builder.setCancelable(true)
        builder.setPositiveButton("설정", DialogInterface.OnClickListener {
            dialog, id ->       // 확인 버튼 설정
            val callGPSSettingIntent = Intent(Settings. ACTION_LOCATION_SOURCE_SETTINGS)
            getGPSPermissionLauncher.launch(callGPSSettingIntent)
        })
        builder.setNegativeButton("취소", DialogInterface.OnClickListener {
            dialog, id -> dialog.cancel()
            Toast.makeText(this@MainActivity, "기기에서 GPS 설정 후 사용해주세요",
                Toast.LENGTH_SHORT).show()
            finish()
        })
        builder.create().show()     // 다이얼로그 생성 및 보여주기
    }
}