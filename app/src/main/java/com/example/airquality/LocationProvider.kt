package com.example.airquality

import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.constraintlayout.motion.widget.Debug.getLocation
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager

class LocationProvider(val context: Context) {

    private var location: Location? = null
    private var locationManager: LocationManager? = null     // 위치 서비스 접근

    init {
        getLocation();      // 초기화 시 위치 가져옴
    }

    private fun getLocation(): Location? {
        try {
            locationManager = context.getSystemService(
                Context.LOCATION_SERVICE) as LocationManager

            var gpsLocation: Location? = null
            var networkLocation: Location? = null

            // GPS와 Network Provider가 활성되어 있는지 확인
            val isGPSEnabled: Boolean =
                locationManager!!.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled: Boolean =
                locationManager!!.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

            if (!isGPSEnabled && !isNetworkEnabled) {
                // 둘다 사용 불가능한 상황이면 null 반환
                return null
            } else {
                // ACCESS_COARSE_LOCATION보다 더 정밀한 위치 정보 얻기
                val hasFineLocationPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION)

                // 도시 Block 단위 정밀도의 위치 정보 얻기
                val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION)

                // 위 두개 권한 없다면 null 반환
                if (hasFineLocationPermission !=
                    PackageManager.PERMISSION_GRANTED ||
                    hasCoarseLocationPermission !=
                    PackageManager.PERMISSION_GRANTED
                ) return null

                // 네트워크 사용이 가능한 경우 위치를 가져옴
                if (isNetworkEnabled) {
                    networkLocation = locationManager?.getLastKnownLocation(
                        LocationManager.NETWORK_PROVIDER)
                }

                // GPS 사용이 가능한 경우 위치를 가져옴
                if (isGPSEnabled) {
                    gpsLocation = locationManager?.getLastKnownLocation(
                        LocationManager.GPS_PROVIDER)
                }

                // 두 개의 위치가 있다면 정확도가 높은 것으로 선택
                if (gpsLocation != null && networkLocation != null) {
                    if (gpsLocation.accuracy > networkLocation.accuracy) {
                        location = gpsLocation
                        return gpsLocation
                    } else {
                        location = networkLocation
                        return networkLocation
                    }
                } else {
                    // 위치 정보가 한 개만 있는 경우
                    if (gpsLocation != null) {
                        location = gpsLocation
                    }
                    if (networkLocation != null) {
                        location = networkLocation
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()     // 에러 출력
        }
        return location
    }

    // 위도 정보를 가져오는 함수
    fun getLocationLatitude(): Double {
        return location?.latitude ?: 0.0        // null이면 0.0 반환
    }

    // 경도 정보를 가져오는 함수
    fun getLocationLongitude(): Double {
        return location?.longitude ?: 0.0        // null이면 0.0 반환
    }
}
