package com.example.rootmap

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.rootmap.databinding.FragmentMenuBinding
import com.example.rootmap.databinding.DialogTouristDetailBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.simplexml.SimpleXmlConverterFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"
private const val LOCATION_PERMISSION_REQUEST_CODE = 1

class MenuFragment : Fragment() {
    private var param1: String? = null
    private var param2: String? = null

    private lateinit var binding: FragmentMenuBinding
    private lateinit var apiService: TouristApiService
    private lateinit var weatherApiService: WeatherApiService
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private var currentAreaCode = 1
    private var currentContentTypeId = 12 // Default to 관광지
    private var retryCount = 0
    private val maxRetries = 5
    private var totalPages = 1
    private var selectedButton: Button? = null
    private lateinit var locationService: LocationService
    private var retryCountWeather = 0
    private val maxRetriesWeather = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }

        // Retrofit 초기화
        val retrofit = Retrofit.Builder()
            .baseUrl("https://apis.data.go.kr/B551011/KorService1/")
            .addConverterFactory(SimpleXmlConverterFactory.create())
            .build()

        apiService = retrofit.create(TouristApiService::class.java)

        // Weather API 초기화
        val weatherRetrofit = Retrofit.Builder()
            .baseUrl("https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/")
            .addConverterFactory(SimpleXmlConverterFactory.create())
            .build()

        weatherApiService = weatherRetrofit.create(WeatherApiService::class.java)



        // Firebase Database 초기화
        database = FirebaseDatabase.getInstance().reference
        auth = FirebaseAuth.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMenuBinding.inflate(inflater, container, false)

        // 도시 목록을 정의
        val cityList = resources.getStringArray(R.array.locations_array)

        // ArrayAdapter를 생성하여 Spinner에 연결
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, cityList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.citySpinner.adapter = adapter

        binding.citySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                currentAreaCode = when (position) {
                    0 -> 1
                    1 -> 2
                    2 -> 3
                    3 -> 4
                    4 -> 5
                    5 -> 6
                    6 -> 7
                    7 -> 8
                    8 -> 31
                    9 -> 32
                    else -> 1
                }
                retryCount = 0 // 스피너가 선택될 때마다 재시도 카운트 초기화
                fetchTotalPages(currentAreaCode, currentContentTypeId) // 스피너 변경 시에도 랜덤 페이지로 가져오기

                // Fetch weather for the selected city
                val nxArray = resources.getIntArray(R.array.location_array_nx)
                val nyArray = resources.getIntArray(R.array.location_array_ny)
                val nx = nxArray[position]
                val ny = nyArray[position]
                val city = cityList[position]
                fetchWeather(nx, ny, city)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 관광 타입 버튼 클릭 리스너 설정
        setupButton(binding.btnTourist, 12)
        setupButton(binding.btnCulture, 14)
        setupButton(binding.btnFestival, 15)
        setupButton(binding.btnCourse, 25)
        setupButton(binding.btnLeisure, 28)
        setupButton(binding.btnLodging, 32)
        setupButton(binding.btnShopping, 38)
        setupButton(binding.btnRestaurant, 39)

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // SwipeRefreshLayout 설정
        binding.swipeRefreshLayout.setOnRefreshListener {
            retryCount = 0 // 새로고침 시 재시도 카운트 초기화
            fetchTotalPages(currentAreaCode, currentContentTypeId) // 새로고침 시에도 랜덤 페이지로 가져오기
        }

        // 드롭다운 메뉴의 기본값을 서울로 설정하고 초기 데이터 로드
        binding.citySpinner.setSelection(0) // 서울이 0번째 인덱스에 있다고 가정
        selectButton(binding.btnTourist) // 관광지 버튼을 선택된 상태로 설정
        fetchTotalPages(1, 12) // 서울의 지역 코드는 1, 관광지는 12, 앱 처음 실행 시 랜덤 페이지로 가져오기

        // LocationService 초기화 및 위치 정보 가져오기
        locationService = LocationService(requireContext())
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            fetchLocation()
        }

        return binding.root
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchLocation()
        }
    }

    private fun setupButton(button: Button, contentTypeId: Int) {
        button.setOnClickListener {
            selectButton(button)
            fetchTotalPages(currentAreaCode, contentTypeId) // 버튼 클릭 시에도 랜덤 페이지로 가져오기
        }
    }

    private fun selectButton(button: Button) {
        clearButtonSelection()
        button.setTextColor(ContextCompat.getColor(requireContext(), R.color.selected_button_text))
        selectedButton = button
    }

    private fun clearButtonSelection() {
        selectedButton?.setTextColor(ContextCompat.getColor(requireContext(), R.color.default_button_text))
    }

    private var isInitialLoad = true

    private fun fetchTotalPages(areaCode: Int, contentTypeId: Int) { // 전체 페이지 수 가져오기
        apiService.getTouristInfo(
            numOfRows = 10,
            pageNo = 1,
            mobileOS = "AND",
            mobileApp = "MobileApp",
            contentTypeId = contentTypeId,
            areaCode = areaCode,
            serviceKey = "iIzVkyvN4jIuoBR82vVZ0iFXlV65w0gsaiuOlUboGQ45v7PnBXkVOsDoBxoqMul10rfSMk7J+X5YKBxqu2ANRQ=="
        ).enqueue(object : Callback<TouristResponse> {
            override fun onResponse(
                call: Call<TouristResponse>,
                response: Response<TouristResponse>
            ) {
                if (response.isSuccessful) {
                    val totalCount = response.body()?.body?.totalCount?.toIntOrNull() ?: 0
                    totalPages = (totalCount / 10) + 1
                    fetchTouristInfo(areaCode, contentTypeId, randomPage = !isInitialLoad)
                    isInitialLoad = false
                } else {
                    Log.e("API_ERROR", "Response code: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<TouristResponse>, t: Throwable) {
                Log.e("API_FAILURE", "Failed to fetch total pages", t)
            }
        })
    }

    private fun fetchTouristInfo(areaCode: Int, contentTypeId: Int, randomPage: Boolean = false) { // 여행지 정보 가져오기
        currentContentTypeId = contentTypeId
        if (retryCount >= maxRetries) {
            binding.swipeRefreshLayout.isRefreshing = false
            return
        }

        val pageNo = if (randomPage) Random.nextInt(1, totalPages + 1) else 1
        apiService.getTouristInfo(
            numOfRows = 10,
            pageNo = pageNo,
            mobileOS = "AND",
            mobileApp = "MobileApp",
            contentTypeId = contentTypeId,
            areaCode = areaCode,
            serviceKey = "iIzVkyvN4jIuoBR82vVZ0iFXlV65w0gsaiuOlUboGQ45v7PnBXkVOsDoBxoqMul10rfSMk7J+X5YKBxqu2ANRQ=="
        ).enqueue(object : Callback<TouristResponse> {
            override fun onResponse(
                call: Call<TouristResponse>,
                response: Response<TouristResponse>
            ) {
                if (response.isSuccessful) {
                    val items = response.body()?.body?.items?.item ?: emptyList()
                    Log.d("API_SUCCESS", "Fetched ${items.size} items")

                    // 각 아이템의 추천 수를 Firebase에서 가져옴
                    items.forEach { item ->
                        item.title?.let { title ->
                            // Firebase 경로에서 사용할 수 없는 문자를 대체
                            val sanitizedTitle = title.replace("[.\\#\\$\\[\\]]".toRegex(), "")
                            database.child("likes").child(sanitizedTitle)
                                .addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                                        val likeCount = dataSnapshot.getValue(Int::class.java) ?: 0
                                        item.likeCount = likeCount
                                        binding.recyclerView.adapter?.notifyDataSetChanged()
                                    }

                                    override fun onCancelled(databaseError: DatabaseError) {
                                        Log.w("MenuFragment", "loadLikeCount:onCancelled", databaseError.toException())
                                    }
                                })

                            // 사용자 하트 상태 가져오기
                            auth.currentUser?.uid?.let { userId ->
                                database.child("userLikes").child(userId).child(sanitizedTitle)
                                    .addListenerForSingleValueEvent(object : ValueEventListener {
                                        override fun onDataChange(dataSnapshot: DataSnapshot) {
                                            val isLiked = dataSnapshot.getValue(Boolean::class.java) ?: false
                                            item.isLiked = isLiked
                                            binding.recyclerView.adapter?.notifyDataSetChanged()
                                        }

                                        override fun onCancelled(databaseError: DatabaseError) {
                                            Log.w("MenuFragment", "loadUserLikeStatus:onCancelled", databaseError.toException())
                                        }
                                    })
                            }
                        }
                    }

                    if (items.isNotEmpty()) {
                        val adapter = TouristAdapter(items, database, auth) { item ->
                            if (currentContentTypeId == 25) {
                                item.contentid?.let { fetchTouristDetail(it) }
                            }
                        }
                        binding.recyclerView.adapter = adapter
                    } else {
                        Log.d("API_SUCCESS", "No items found")
                        retryFetchTouristInfo(areaCode, contentTypeId) // 데이터가 없으면 재시도
                    }
                } else {
                    Log.e("API_ERROR", "Response code: ${response.code()}")
                    retryFetchTouristInfo(areaCode, contentTypeId) // 에러 발생 시 재시도
                }
                binding.swipeRefreshLayout.isRefreshing = false
            }

            override fun onFailure(call: Call<TouristResponse>, t: Throwable) {
                // 에러 처리
                Log.e("API_FAILURE", "Failed to fetch data", t)
                retryFetchTouristInfo(areaCode, contentTypeId) // 실패 시 재시도
                binding.swipeRefreshLayout.isRefreshing = false
            }
        })
    }

    private fun fetchTouristDetail(contentId: String) {
        apiService.getTouristDetail(
            contentId = contentId.toInt(),
            contentTypeId = currentContentTypeId,
            mobileOS = "AND",
            mobileApp = "MobileApp",
            serviceKey = "iIzVkyvN4jIuoBR82vVZ0iFXlV65w0gsaiuOlUboGQ45v7PnBXkVOsDoBxoqMul10rfSMk7J+X5YKBxqu2ANRQ=="
        ).enqueue(object : Callback<TouristDetailResponse> {
            override fun onResponse(
                call: Call<TouristDetailResponse>,
                response: Response<TouristDetailResponse>
            ) {
                if (response.isSuccessful) {
                    val detail = response.body()?.body?.items?.item ?: return
                    showTouristDetailDialog(detail)
                } else {
                    Log.e("API_ERROR", "Detail response code: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<TouristDetailResponse>, t: Throwable) {
                Log.e("API_FAILURE", "Failed to fetch detail data", t)
            }
        })
    }

    private fun showTouristDetailDialog(detail: TouristDetail) {
        val dialogBinding =
            DialogTouristDetailBinding.inflate(LayoutInflater.from(requireContext()))

        dialogBinding.theme.text = detail.theme
        dialogBinding.schedule.text = detail.schedule
        dialogBinding.distance.text = detail.distance
        dialogBinding.taketime.text = detail.taketime
        dialogBinding.infocentertourcourse.text = detail.infocentertourcourse

        AlertDialog.Builder(requireContext())
            .setTitle("코스 정보")
            .setView(dialogBinding.root)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun retryFetchTouristInfo(areaCode: Int, contentTypeId: Int) {
        retryCount++
        fetchTouristInfo(areaCode, contentTypeId, randomPage = true) // 재시도 시에도 랜덤 페이지로 가져오기
    }


    private fun fetchLocation() {
        locationService.fetchLocation { latitude, longitude ->
            activity?.runOnUiThread {
                //val tvLocation = binding.root.findViewById<TextView>(R.id.tvLocation)
                //tvLocation.text = "위도: $latitude, 경도: $longitude"
            }
        }
    }

    private fun fetchWeather(nx: Int, ny: Int, city: String) {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HHmm", Locale.getDefault())
        val currentTime = Calendar.getInstance()

        val baseDate = dateFormat.format(Date())
        val baseTime = timeFormat.format(currentTime.time).let {
            val hour = currentTime.get(Calendar.HOUR_OF_DAY)
            val minute = currentTime.get(Calendar.MINUTE)

            if (minute >= 40) {
                String.format("%02d00", hour)
            } else {
                String.format("%02d00", if (hour == 0) 23 else hour - 1)
            }
        }

        // 초단기 실황 -> 현재기온
        weatherApiService.getUltraSrtNcst(
            serviceKey = "oX0uqL6VzriCMyNDlwDB23W4%2Bb9mn8EPDaqry2QN4hO9qaQCMH5oQOhK9oIi92TiDYQ6vAY9nv9XDubAGOdugw%3D%3D",
            numOfRows = 10,
            pageNo = 1,
            dataType = "XML",
            baseDate = baseDate,
            baseTime = baseTime,
            nx = nx,
            ny = ny
        ).enqueue(object : Callback<WeatherResponse> {
            override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                if (response.isSuccessful) {
                    Log.d("WEATHER_RESPONSE", response.body().toString())
                    val items = response.body()?.body?.items?.item
                    val temperatureItem = items?.find { it.category == "T1H" }
                    val precipitationItem = items?.find { it.category == "PTY" }

                    if (temperatureItem != null) {
                        Log.d("WEATHER_SUCCESS", "Temperature: ${temperatureItem.obsrValue}")
                        updateWeatherInfo(temperatureItem.obsrValue, city)
                    } else {
                        Log.e("WEATHER_ERROR", "Temperature data not found")
                        handleWeatherFailure(nx, ny, city)
                    }

                    if (precipitationItem != null) {
                        Log.d("WEATHER_SUCCESS", "Precipitation Type: ${precipitationItem.obsrValue}")
                        updatePrecipitationInfo(precipitationItem.obsrValue, items?.find { it.category == "SKY" }?.fcstValue)
                    } else {
                        Log.e("WEATHER_ERROR", "Precipitation data not found")
                        handleWeatherFailure(nx, ny, city)
                    }

                    retryCountWeather = 0 // Reset retry count on success
                } else {
                    Log.e("WEATHER_ERROR", "Response code: ${response.code()}")
                    Log.e("WEATHER_ERROR_BODY", response.errorBody()?.string() ?: "No error body")
                    handleWeatherFailure(nx, ny, city)
                }
            }

            override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                Log.e("WEATHER_FAILURE", "Failed to fetch weather data", t)
                handleWeatherFailure(nx, ny, city)
            }
        })

        // 단기 예보
        weatherApiService.getVilageFcst(
            serviceKey = "oX0uqL6VzriCMyNDlwDB23W4%2Bb9mn8EPDaqry2QN4hO9qaQCMH5oQOhK9oIi92TiDYQ6vAY9nv9XDubAGOdugw%3D%3D",
            numOfRows = 50,
            pageNo = 1,
            dataType = "XML",
            baseDate = baseDate,
            baseTime = "0200",  // 단기 예보의 baseTime은 0200 고정
            nx = nx,
            ny = ny
        ).enqueue(object : Callback<WeatherResponse> {
            override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                if (response.isSuccessful) {
                    Log.d("WEATHER_RESPONSE", "Short-term: ${response.body().toString()}")
                    val items = response.body()?.body?.items?.item
                    //val tmx = items?.find { it.category == "TMX" }?.fcstValue
                    //val tmn = items?.find { it.category == "TMN" }?.fcstValue
                    val sky = items?.find { it.category == "SKY" }?.fcstValue
                    val reh = items?.find { it.category == "REH" }?.fcstValue

                    //Log.d("WEATHER_SUCCESS", "Today's Temperature: TMX: ${tmx ?: "null"}, TMN: ${tmn ?: "null"}")
                    //updateShortTermWeatherInfo(tmx, tmn)
                    retryCountWeather = 0 // Reset retry count on success
                    Log.d("WEATHER_SUCCESS", "Sky Condition: ${sky ?: "null"}")
                    Log.d("WEATHER_SUCCESS", "Humidity Condition: ${reh ?: "null"}")
                    updateSkyInfo(sky)
                    updateHumidityInfo(reh)
                } else {
                    Log.e("WEATHER_ERROR", "Short-term Forecast Response code: ${response.code()}")
                    Log.e("WEATHER_ERROR_BODY", response.errorBody()?.string() ?: "No error body")
                    handleWeatherFailure(nx, ny, city)
                }
            }

            override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                Log.e("WEATHER_FAILURE", "Failed to fetch short-term forecast data", t)
                handleWeatherFailure(nx, ny, city)
            }
        })
    }

    private fun updateWeatherInfo(temp: String?, city: String) {
        val tvNowCelsius = binding.root.findViewById<TextView>(R.id.tvNowCelsius)
        val tvLocation = binding.root.findViewById<TextView>(R.id.tvLocation)

        Log.d("WEATHER_UPDATE", "Updating temperature to ${temp ?: "null"}° for city $city")

        if (temp != null) {
            tvNowCelsius.text = "$temp°"
        }
        tvLocation.text = city
    }

    private fun updatePrecipitationInfo(ptyValue: String?, skyValue: String?) {
        val imageView = binding.root.findViewById<ImageView>(R.id.imageView)
        val tvSkyCondition = binding.root.findViewById<TextView>(R.id.tvSkyCondition)

        if (ptyValue == "0" || ptyValue == null) {
            // If no precipitation, update based on sky condition
            val skyCondition = when (skyValue) {
                "1" -> {
                    imageView.setImageResource(R.drawable.weather_01)
                    "맑음"
                }
                "2" -> {
                    imageView.setImageResource(R.drawable.weather_02)
                    "구름 조금"
                }
                "3" -> {
                    imageView.setImageResource(R.drawable.weather_02)
                    "구름 많음"
                }
                "4" -> {
                    imageView.setImageResource(R.drawable.weather_04)
                    "흐림"
                }
                else -> null
            }

            if (skyCondition != null) {
                Log.d("WEATHER_UPDATE", "Updating sky condition to $skyCondition")
                tvSkyCondition.text = "하늘: $skyCondition"
            }
        } else {
            // If precipitation exists, update to rain icon
            imageView.setImageResource(R.drawable.weather_03)
            tvSkyCondition.text = "비"
            Log.d("WEATHER_UPDATE", "Updating to rain condition")
        }
    }


    private fun updateSkyInfo(skyValue: String?) {
        val tvSkyCondition = binding.root.findViewById<TextView>(R.id.tvSkyCondition)
        val imageView = binding.root.findViewById<ImageView>(R.id.imageView)
        val skyCondition = when (skyValue) {
            "1" -> {
                imageView.setImageResource(R.drawable.weather_01)
                "맑음"
            }
            "2" -> {
                imageView.setImageResource(R.drawable.weather_02)
                "구름 조금"
            }
            "3" -> {
                imageView.setImageResource(R.drawable.weather_02)
                "구름 많음"
            }
            "4" -> {
                imageView.setImageResource(R.drawable.weather_04)
                "흐림"
            }
            else -> null
        }

        if (skyCondition != null) {
            Log.d("WEATHER_UPDATE", "Updating sky condition to $skyCondition")
            tvSkyCondition.text = "하늘: $skyCondition"
        }
    }

    private fun updateHumidityInfo(rehValue: String?) {
        val tvHumidity = binding.root.findViewById<TextView>(R.id.tvHumidity)

        if (rehValue != null) {
            Log.d("WEATHER_UPDATE", "Updating humidity to $rehValue%")
            tvHumidity.text = "습도: $rehValue%"
        } else {
            Log.d("WEATHER_UPDATE", "Humidity data not available")
            //tvHumidity.text = "습도: N/A"
        }
    }

    private fun handleWeatherFailure(nx: Int, ny: Int, city: String) {
        if (retryCountWeather < maxRetriesWeather) {
            retryCountWeather++
            Log.d("WEATHER_RETRY", "Retrying fetchWeather... Attempt: $retryCountWeather")
            fetchWeather(nx, ny, city)
        } else {
            Log.e("WEATHER_RETRY", "Max retries reached. Could not fetch weather data.")
            //updateWeatherInfo(null, city)
            //updateSkyInfo(null)
            //updateHumidityInfo(null)
        }
    }

    /*
    private fun updateShortTermWeatherInfo(tmx: String?, tmn: String?) {
        val tvTodayCelsius = binding.root.findViewById<TextView>(R.id.tvTodayCelsius)
        Log.d("WEATHER_UPDATE", "Updating today's temperature: TMX: ${tmx ?: "null"}°, TMN: ${tmn ?: "null"}°")

        if (tmx != null && tmn != null) {
            tvTodayCelsius.text = "최고: $tmx°/최저: $tmn°"
        }
    }*/


    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            MenuFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}