package com.example.rootmap

import android.Manifest
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.rootmap.databinding.FragmentMenuBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import de.hdodenhof.circleimageview.CircleImageView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.simplexml.SimpleXmlConverterFactory
import java.lang.Math.atan2
import java.lang.Math.cos
import java.lang.Math.sin
import java.lang.Math.sqrt
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.CompletableFuture
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

    // API에 있는 지역 리스트와 좌표 정의
    data class Area(val name: String, val code: Int, val latitude: Double, val longitude: Double)

    val areas = listOf(
        Area("서울", 1, 37.5665, 126.9780),
        Area("인천", 2, 37.4563, 126.7052),
        Area("대전", 3, 36.3504, 127.3845),
        Area("대구", 4, 35.8722, 128.6018),
        Area("광주", 5, 35.1595, 126.8526),
        Area("부산", 6, 35.1796, 129.0756),
        Area("울산", 7, 35.5384, 129.3114),
        Area("세종특별자치시", 8, 36.4801, 127.2892),
        Area("경기도", 31, 37.4138, 127.5183),
        Area("강원도", 32, 37.8228, 128.1555)
    )

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
            .addConverterFactory(SimpleXmlConverterFactory.createNonStrict())  // NonStrict로 변경
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

        // 시간에 따라 배경 설정
        setWeatherBackground()

        // 도시 목록을 정의
        val cityList = resources.getStringArray(R.array.locations_array)

        // ArrayAdapter를 생성하여 Spinner에 연결
        val adapter = ArrayAdapter(requireContext(), R.layout.spinner_color, cityList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.citySpinner.adapter = adapter

        binding.citySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (position == 0) {
                    fetchLocation { areaCode ->
                        currentAreaCode = areaCode
                        fetchTotalPages(currentAreaCode, currentContentTypeId)
                    }
                } else {
                    currentAreaCode = when (position) {
                        1 -> 1
                        2 -> 2
                        3 -> 3
                        4 -> 4
                        5 -> 5
                        6 -> 6
                        7 -> 7
                        8 -> 8
                        9 -> 31
                        10 -> 32
                        else -> 1
                    }
                    fetchTotalPages(currentAreaCode, currentContentTypeId) // 스피너 변경 시에도 랜덤 페이지로 가져오기

                    // 지정된 도시의 날씨 가져오기
                    val nxArray = resources.getIntArray(R.array.location_array_nx)
                    val nyArray = resources.getIntArray(R.array.location_array_ny)
                    val nx = nxArray[position]
                    val ny = nyArray[position]
                    val city = cityList[position]
                    fetchWeather(nx, ny, city)
                }
                retryCount = 0 // 스피너가 선택될 때마다 재시도 카운트 초기화

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

        // 드롭다운 메뉴의 기본값을 내 위치로 설정하고 초기 데이터 로드
        binding.citySpinner.setSelection(0) // 내 위치를 선택된 상태로 설정
        selectButton(binding.btnTourist) // 관광지 버튼을 선택된 상태로 설정

        // LocationService 초기화 및 위치 정보 가져오기
        locationService = LocationService(requireContext())
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            fetchLocation { areaCode ->
                currentAreaCode = areaCode
                fetchTotalPages(currentAreaCode, currentContentTypeId)
            }
        }

        // locationButton 클릭 리스너 설정
        binding.locationButton.setOnClickListener {
            binding.locationButtonText.text = "현재 위치 확인 중..."
            binding.citySpinner.setSelection(0)
            fetchLocation { areaCode ->
                currentAreaCode = areaCode
                fetchTotalPages(currentAreaCode, currentContentTypeId)
            }
        }

        showLoadingDialog()
        fetchTotalPages(currentAreaCode, currentContentTypeId)

        return binding.root
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchLocation { areaCode ->
                currentAreaCode = areaCode
                fetchTotalPages(currentAreaCode, currentContentTypeId)
            }
        }
    }

    private fun setupButton(button: Button, contentTypeId: Int) {
        button.setOnClickListener {
            selectButton(button)
            showLoadingDialog()
            fetchTotalPages(currentAreaCode, contentTypeId) // 버튼 클릭 시에도 랜덤 페이지로 가져오기
        }
    }

    private fun selectButton(button: Button) {
        clearButtonSelection()
        button.setTextColor(ContextCompat.getColor(requireContext(), R.color.selected_button_text))
        button.setTypeface(ResourcesCompat.getFont(requireContext(), R.font.pretendard_medium), android.graphics.Typeface.BOLD) // 폰트 적용
        selectedButton = button
    }

    private fun clearButtonSelection() {
        selectedButton?.let {
            it.setTextColor(ContextCompat.getColor(requireContext(), R.color.default_button_text))
            it.setTypeface(ResourcesCompat.getFont(requireContext(), R.font.pretendard_medium), android.graphics.Typeface.NORMAL) // 폰트 적용
        }
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

    private fun fetchTouristInfo(areaCode: Int, contentTypeId: Int, randomPage: Boolean = false) {
        currentContentTypeId = contentTypeId
        if (retryCount >= maxRetries) {
            binding.swipeRefreshLayout.isRefreshing = false
            hideLoadingDialog()
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

                    val tasks = items.map { item ->
                        val completableFuture = CompletableFuture<Void>()
                        item.title?.let { title ->
                            // Firebase path sanitization
                            val sanitizedTitle = title.replace("[.\\#\\$\\[\\]]".toRegex(), "")
                            database.child("likes").child(sanitizedTitle).addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(dataSnapshot: DataSnapshot) {
                                    item.likeCount = dataSnapshot.getValue(Int::class.java) ?: 0
                                    completableFuture.complete(null)
                                }

                                override fun onCancelled(databaseError: DatabaseError) {
                                    Log.w("MenuFragment", "loadLikeCount:onCancelled", databaseError.toException())
                                    completableFuture.completeExceptionally(databaseError.toException())
                                }
                            })

                            auth.currentUser?.uid?.let { userId ->
                                database.child("userLikes").child(userId).child(sanitizedTitle).addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                                        item.isLiked = dataSnapshot.getValue(Boolean::class.java) ?: false
                                        completableFuture.complete(null)
                                    }

                                    override fun onCancelled(databaseError: DatabaseError) {
                                        Log.w("MenuFragment", "loadUserLikeStatus:onCancelled", databaseError.toException())
                                        completableFuture.completeExceptionally(databaseError.toException())
                                    }
                                })
                            }
                        }
                        completableFuture
                    }

                    CompletableFuture.allOf(*tasks.toTypedArray()).thenRun {
                        if (items.isNotEmpty()) {
                            val adapter = TouristAdapter(
                                items = items,
                                database = database,
                                auth = auth,
                                menuFragment = this@MenuFragment,
                                onItemClick = { item -> item.contentid?.let { fetchTouristDetailIntro(it, currentContentTypeId) } },
                                addButtonVisible = true // MenuFragment에서는 addButton을 보이도록 설정
                            )
                            binding.recyclerView.adapter = adapter

                            if (currentContentTypeId == 25) {
                                items.forEach { it.addButtonVisible = false }
                            }

                            binding.recyclerView.adapter = adapter
                        } else {
                            Log.d("API_SUCCESS", "No items found")
                            retryFetchTouristInfo(areaCode, contentTypeId) // 데이터가 없으면 재시도
                        }
                        binding.swipeRefreshLayout.isRefreshing = false
                        hideLoadingDialog()
                    }.exceptionally {
                        binding.swipeRefreshLayout.isRefreshing = false
                        hideLoadingDialog()
                        null
                    }
                } else {
                    Log.e("API_ERROR", "Response code: ${response.code()}")
                    retryFetchTouristInfo(areaCode, contentTypeId) // 에러 발생 시 재시도
                    binding.swipeRefreshLayout.isRefreshing = false
                    hideLoadingDialog()
                }
            }

            override fun onFailure(call: Call<TouristResponse>, t: Throwable) {
                // 에러 처리
                Log.e("API_FAILURE", "Failed to fetch data", t)
                retryFetchTouristInfo(areaCode, contentTypeId) // 실패 시 재시도
                binding.swipeRefreshLayout.isRefreshing = false
                hideLoadingDialog()
            }
        })
    }

    private lateinit var loadingDialog: ProgressDialog
    private fun showLoadingDialog() {
        loadingDialog = ProgressDialog(requireContext()).apply {
            window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setCancelable(false)
            show()
            setContentView(R.layout.custom_progress_dialog)
            val gifImageView : ImageView = findViewById(R.id.frame_loading)
            Glide.with(requireContext()).load(R.raw.loading).circleCrop().into(gifImageView)
        }
    }
    private fun hideLoadingDialog() {
        if (::loadingDialog.isInitialized && loadingDialog.isShowing) {
            loadingDialog.dismiss()
        }
    }

    fun removeHtmlTags(input: String?): String {
        return input?.replace(Regex("<[^>]*>"), "")?.replace("<br>", "") ?: ""
    }

    fun fetchTouristDetailIntro(contentId: String, contentTypeId: Int) {
        showLoadingDialog()  // 로딩 다이얼로그 표시
        apiService.getTouristDetail(
            contentId = contentId.toInt(),
            contentTypeId = contentTypeId,
            mobileOS = "AND",
            mobileApp = "MobileApp",
            serviceKey = "iIzVkyvN4jIuoBR82vVZ0iFXlV65w0gsaiuOlUboGQ45v7PnBXkVOsDoBxoqMul10rfSMk7J+X5YKBxqu2ANRQ=="
        ).enqueue(object : Callback<TouristDetailResponse> {
            override fun onResponse(
                call: Call<TouristDetailResponse>,
                response: Response<TouristDetailResponse>
            ) {
                hideLoadingDialog()  // 로딩 다이얼로그 숨김
                if (response.isSuccessful) {
                    val detail = response.body()?.body?.items?.item ?: return

                    // 팝업창 내용 중간에 들어간 태그 제거
                    detail.accomcount = removeHtmlTags(detail.accomcount)
                    detail.chkbabycarriage = removeHtmlTags(detail.chkbabycarriage)
                    detail.chkcreditcard = removeHtmlTags(detail.chkcreditcard)
                    detail.chkpet = removeHtmlTags(detail.chkpet)
                    detail.expagerange = removeHtmlTags(detail.expagerange)
                    detail.expguide = removeHtmlTags(detail.expguide)
                    detail.heritage1 = removeHtmlTags(detail.heritage1)
                    detail.heritage2 = removeHtmlTags(detail.heritage2)
                    detail.heritage3 = removeHtmlTags(detail.heritage3)
                    detail.infocenter = removeHtmlTags(detail.infocenter)
                    detail.opendate = removeHtmlTags(detail.opendate)
                    detail.parking = removeHtmlTags(detail.parking)
                    detail.restdate = removeHtmlTags(detail.restdate)
                    detail.useseason = removeHtmlTags(detail.useseason)
                    detail.usetime = removeHtmlTags(detail.usetime)
                    detail.reservationurl = removeHtmlTags(detail.reservationurl)
                    detail.reservationlodging = removeHtmlTags(detail.reservationlodging)
                    detail.eventplace = removeHtmlTags(detail.eventplace)
                    detail.parkingfee = removeHtmlTags(detail.parkingfee)
                    detail.parkingfeeleports = removeHtmlTags(detail.parkingfeeleports)
                    detail.checkintime = removeHtmlTags(detail.checkintime)
                    detail.usetimeculture = removeHtmlTags(detail.usetimeculture)
                    detail.parkingculture = removeHtmlTags(detail.parkingculture)
                    detail.restdateculture = removeHtmlTags(detail.restdateculture)
                    detail.usefee = removeHtmlTags(detail.usefee)
                    detail.scale = removeHtmlTags(detail.scale)
                    detail.usetimefestival = removeHtmlTags(detail.usetimefestival)
                    detail.playtime = removeHtmlTags(detail.playtime)
                    detail.parkingleports = removeHtmlTags(detail.parkingleports)
                    detail.usefeeleports = removeHtmlTags(detail.usefeeleports)
                    detail.opentimefood = removeHtmlTags(detail.opentimefood)
                    detail.parkingfood = removeHtmlTags(detail.parkingfood)
                    detail.restdatefood = removeHtmlTags(detail.restdatefood)
                    detail.opentimefood = removeHtmlTags(detail.opentimefood)
                    detail.checkouttime = removeHtmlTags(detail.checkouttime)
                    detail.usetimeleports = removeHtmlTags(detail.usetimeleports)
                    detail.placeinfo = removeHtmlTags(detail.placeinfo)

                    showTouristDetailDialog(detail)
                } else {
                    Log.e("API_ERROR", "Detail response code: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<TouristDetailResponse>, t: Throwable) {
                hideLoadingDialog()  // 로딩 다이얼로그 숨김
                Log.e("API_FAILURE", "Failed to fetch detail data", t)
            }
        })
    }

    private fun showTouristDetailDialog(detail: TouristDetail) {
        val inflater = LayoutInflater.from(requireContext())
        val dialogBinding: View

        when (currentContentTypeId) {
            12 -> dialogBinding = inflater.inflate(R.layout.dialog_tourist_detail_12, null)
            14 -> dialogBinding = inflater.inflate(R.layout.dialog_tourist_detail_14, null)
            15 -> dialogBinding = inflater.inflate(R.layout.dialog_tourist_detail_15, null)
            25 -> dialogBinding = inflater.inflate(R.layout.dialog_tourist_detail_25, null)
            28 -> dialogBinding = inflater.inflate(R.layout.dialog_tourist_detail_28, null)
            32 -> dialogBinding = inflater.inflate(R.layout.dialog_tourist_detail_32, null)
            38 -> dialogBinding = inflater.inflate(R.layout.dialog_tourist_detail_38, null)
            39 -> dialogBinding = inflater.inflate(R.layout.dialog_tourist_detail_39, null)
            else -> return // 만약에 지원되지 않는 contentTypeId라면 다이얼로그를 보여주지 않음
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding)
            .setPositiveButton("확인", null)
            .create()

        when (currentContentTypeId) {
            12 -> { // 관광지
                dialogBinding.findViewById<TextView>(R.id.accomcount).text = detail.accomcount
                dialogBinding.findViewById<TextView>(R.id.chkbabycarriage).text = detail.chkbabycarriage
                dialogBinding.findViewById<TextView>(R.id.chkcreditcard).text = detail.chkcreditcard
                dialogBinding.findViewById<TextView>(R.id.chkpet).text = detail.chkpet
                dialogBinding.findViewById<TextView>(R.id.expagerange).text = detail.expagerange
                dialogBinding.findViewById<TextView>(R.id.expguide).text = detail.expguide
                // 유무를 1과 0이 아닌, O와 X로 표시
                dialogBinding.findViewById<TextView>(R.id.heritage1).text = if (detail.heritage1 == "1") "O" else "X"
                dialogBinding.findViewById<TextView>(R.id.heritage2).text = if (detail.heritage2 == "1") "O" else "X"
                dialogBinding.findViewById<TextView>(R.id.heritage3).text = if (detail.heritage3 == "1") "O" else "X"
                dialogBinding.findViewById<TextView>(R.id.infocenter).apply {
                    text = detail.infocenter
                    // 전화번호에 대한 자동 링크 설정
                    autoLinkMask = Linkify.PHONE_NUMBERS
                    movementMethod = LinkMovementMethod.getInstance()
                }
                dialogBinding.findViewById<TextView>(R.id.opendate).text = detail.opendate
                dialogBinding.findViewById<TextView>(R.id.parking).text = detail.parking
                dialogBinding.findViewById<TextView>(R.id.restdate).text = detail.restdate
                dialogBinding.findViewById<TextView>(R.id.useseason).text = detail.useseason
                dialogBinding.findViewById<TextView>(R.id.usetime).text = detail.usetime
            }
            14 -> { // 문화시설
                dialogBinding.findViewById<TextView>(R.id.accomcountculture).text = detail.accomcountculture
                dialogBinding.findViewById<TextView>(R.id.chkbabycarriageculture).text = detail.chkbabycarriageculture
                dialogBinding.findViewById<TextView>(R.id.chkcreditcardculture).text = detail.chkcreditcardculture
                dialogBinding.findViewById<TextView>(R.id.chkpetculture).text = detail.chkpetculture
                dialogBinding.findViewById<TextView>(R.id.discountinfo).text = detail.discountinfo
                dialogBinding.findViewById<TextView>(R.id.infocenterculture).apply {
                    text = detail.infocenter
                    // 전화번호에 대한 자동 링크 설정
                    autoLinkMask = Linkify.PHONE_NUMBERS
                    movementMethod = LinkMovementMethod.getInstance()
                }
                dialogBinding.findViewById<TextView>(R.id.parkingculture).text = detail.parkingculture
                dialogBinding.findViewById<TextView>(R.id.parkingfee).text = detail.parkingfee
                dialogBinding.findViewById<TextView>(R.id.restdateculture).text = detail.restdateculture
                dialogBinding.findViewById<TextView>(R.id.usefee).text = detail.usefee
                dialogBinding.findViewById<TextView>(R.id.usetimeculture).text = detail.usetimeculture
                dialogBinding.findViewById<TextView>(R.id.scale).text = detail.scale
                dialogBinding.findViewById<TextView>(R.id.spendtime).text = detail.spendtime
            }
            15 -> { // 축제공연행사
                dialogBinding.findViewById<TextView>(R.id.agelimit).text = detail.agelimit

                val bookingUrlTextView = dialogBinding.findViewById<TextView>(R.id.bookingplace)
                val bookingUrl = removeHtmlTags(detail.bookingplace)
                bookingUrlTextView.text = bookingUrl
                bookingUrlTextView.autoLinkMask = Linkify.WEB_URLS
                bookingUrlTextView.movementMethod = LinkMovementMethod.getInstance()

                dialogBinding.findViewById<TextView>(R.id.discountinfofestival).text = detail.discountinfofestival
                dialogBinding.findViewById<TextView>(R.id.eventenddate).text = detail.eventenddate

                val homepageUrlTextView = dialogBinding.findViewById<TextView>(R.id.eventhomepage)
                val homepageUrl = removeHtmlTags(detail.eventhomepage)
                homepageUrlTextView.text = homepageUrl
                homepageUrlTextView.autoLinkMask = Linkify.WEB_URLS
                homepageUrlTextView.movementMethod = LinkMovementMethod.getInstance()

                dialogBinding.findViewById<TextView>(R.id.eventplace).text = detail.eventplace
                dialogBinding.findViewById<TextView>(R.id.eventstartdate).text = detail.eventstartdate
                dialogBinding.findViewById<TextView>(R.id.festivalgrade).text = detail.festivalgrade
                dialogBinding.findViewById<TextView>(R.id.placeinfo).text = detail.placeinfo
                dialogBinding.findViewById<TextView>(R.id.playtime).text = detail.playtime
                dialogBinding.findViewById<TextView>(R.id.program).text = detail.program
                dialogBinding.findViewById<TextView>(R.id.spendtimefestival).text = detail.spendtimefestival
                dialogBinding.findViewById<TextView>(R.id.sponsor1).text = detail.sponsor1
                dialogBinding.findViewById<TextView>(R.id.sponsor1tel).apply {
                    text = detail.infocenter
                    // 전화번호에 대한 자동 링크 설정
                    autoLinkMask = Linkify.PHONE_NUMBERS
                    movementMethod = LinkMovementMethod.getInstance()
                }
                dialogBinding.findViewById<TextView>(R.id.sponsor2).text = detail.sponsor2
                dialogBinding.findViewById<TextView>(R.id.sponsor2tel).apply {
                    text = detail.infocenter
                    // 전화번호에 대한 자동 링크 설정
                    autoLinkMask = Linkify.PHONE_NUMBERS
                    movementMethod = LinkMovementMethod.getInstance()
                }
                dialogBinding.findViewById<TextView>(R.id.subevent).text = detail.subevent
                dialogBinding.findViewById<TextView>(R.id.usetimefestival).text = detail.usetimefestival
            }
            25 -> { // 여행코스
                dialogBinding.findViewById<TextView>(R.id.distance).text = detail.distance
                dialogBinding.findViewById<TextView>(R.id.infocentertourcourse).apply {
                    text = detail.infocenter
                    // 전화번호에 대한 자동 링크 설정
                    autoLinkMask = Linkify.PHONE_NUMBERS
                    movementMethod = LinkMovementMethod.getInstance()
                }
                dialogBinding.findViewById<TextView>(R.id.schedule).text = detail.schedule
                dialogBinding.findViewById<TextView>(R.id.taketime).text = detail.taketime
                dialogBinding.findViewById<TextView>(R.id.theme).text = detail.theme

                val detailsButton = dialogBinding.findViewById<Button>(R.id.detailsButton)
                detailsButton.setOnClickListener {
                    val intent = Intent(requireContext(), DetailInfoActivity::class.java)
                    intent.putExtra("contentId", detail.contentid)
                    intent.putExtra("contentTypeId", currentContentTypeId)
                    startActivity(intent)
                }
            }
            28 -> { // 레포츠
                dialogBinding.findViewById<TextView>(R.id.accomcountleports).text = detail.accomcountleports
                dialogBinding.findViewById<TextView>(R.id.chkbabycarriageleports).text = detail.chkbabycarriageleports
                dialogBinding.findViewById<TextView>(R.id.chkcreditcardleports).text = detail.chkcreditcardleports
                dialogBinding.findViewById<TextView>(R.id.chkpetleports).text = detail.chkpetleports
                dialogBinding.findViewById<TextView>(R.id.expagerangeleports).text = detail.expagerangeleports
                dialogBinding.findViewById<TextView>(R.id.infocenterleports).apply {
                    text = detail.infocenter
                    // 전화번호에 대한 자동 링크 설정
                    autoLinkMask = Linkify.PHONE_NUMBERS
                    movementMethod = LinkMovementMethod.getInstance()
                }
                dialogBinding.findViewById<TextView>(R.id.openperiod).text = detail.openperiod
                dialogBinding.findViewById<TextView>(R.id.parkingfeeleports).text = detail.parkingfeeleports
                dialogBinding.findViewById<TextView>(R.id.parkingleports).text = detail.parkingleports
                dialogBinding.findViewById<TextView>(R.id.reservation).apply {
                    text = detail.infocenter
                    // 전화번호에 대한 자동 링크 설정
                    autoLinkMask = Linkify.PHONE_NUMBERS
                    movementMethod = LinkMovementMethod.getInstance()
                }
                dialogBinding.findViewById<TextView>(R.id.restdateleports).text = detail.restdateleports
                dialogBinding.findViewById<TextView>(R.id.scaleleports).text = detail.scaleleports
                dialogBinding.findViewById<TextView>(R.id.usefeeleports).text = detail.usefeeleports
                dialogBinding.findViewById<TextView>(R.id.usetimeleports).text = detail.usetimeleports
            }
            32 -> { // 숙박
                dialogBinding.findViewById<TextView>(R.id.accomcountlodging).text = detail.accomcountlodging
                dialogBinding.findViewById<TextView>(R.id.benikia).text = if(detail.benikia == "1") "O" else "X"
                dialogBinding.findViewById<TextView>(R.id.checkintime).text = detail.checkintime
                dialogBinding.findViewById<TextView>(R.id.checkouttime).text = detail.checkouttime
                dialogBinding.findViewById<TextView>(R.id.chkcooking).text = if(detail.chkcooking == "1") "O" else "X"
                dialogBinding.findViewById<TextView>(R.id.foodplace).text = detail.foodplace
                dialogBinding.findViewById<TextView>(R.id.goodstay).text = if(detail.goodstay == "1") "O" else "X"
                dialogBinding.findViewById<TextView>(R.id.hanok).text = if(detail.hanok == "1") "O" else "X"
                dialogBinding.findViewById<TextView>(R.id.infocenterlodging).apply {
                    text = detail.infocenter
                    // 전화번호에 대한 자동 링크 설정
                    autoLinkMask = Linkify.PHONE_NUMBERS
                    movementMethod = LinkMovementMethod.getInstance()
                }
                dialogBinding.findViewById<TextView>(R.id.parkinglodging).text = detail.parkinglodging
                dialogBinding.findViewById<TextView>(R.id.pickup).text = detail.pickup
                dialogBinding.findViewById<TextView>(R.id.roomcount).text = detail.roomcount
                dialogBinding.findViewById<TextView>(R.id.reservationlodging).apply {
                    text = detail.infocenter
                    // 전화번호에 대한 자동 링크 설정
                    autoLinkMask = Linkify.PHONE_NUMBERS
                    movementMethod = LinkMovementMethod.getInstance()
                }
                dialogBinding.findViewById<TextView>(R.id.roomtype).text = detail.roomtype
                dialogBinding.findViewById<TextView>(R.id.scalelodging).text = detail.scalelodging
                dialogBinding.findViewById<TextView>(R.id.subfacility).text = detail.subfacility
                dialogBinding.findViewById<TextView>(R.id.barbecue).text = if(detail.barbecue == "1") "O" else "X"
                dialogBinding.findViewById<TextView>(R.id.beauty).text = if(detail.beauty == "1") "O" else "X"
                dialogBinding.findViewById<TextView>(R.id.beverage).text = if(detail.beverage == "1") "O" else "X"
                dialogBinding.findViewById<TextView>(R.id.bicycle).text = if(detail.bicycle == "1") "O" else "X"
                dialogBinding.findViewById<TextView>(R.id.campfire).text = if(detail.campfire == "1") "O" else "X"
                dialogBinding.findViewById<TextView>(R.id.fitness).text = if(detail.fitness == "1") "O" else "X"
                dialogBinding.findViewById<TextView>(R.id.karaoke).text = if(detail.karaoke == "1") "O" else "X"
                dialogBinding.findViewById<TextView>(R.id.publicbath).text = if(detail.publicbath == "1") "O" else "X"
                dialogBinding.findViewById<TextView>(R.id.publicpc).text = if(detail.publicpc == "1") "O" else "X"
                dialogBinding.findViewById<TextView>(R.id.sauna).text = if(detail.sauna == "1") "O" else "X"
                dialogBinding.findViewById<TextView>(R.id.seminar).text = if(detail.seminar == "1") "O" else "X"
                dialogBinding.findViewById<TextView>(R.id.sports).text = if(detail.sports == "1") "O" else "X"
                dialogBinding.findViewById<TextView>(R.id.refundregulation).text = detail.refundregulation

                // 예약안내홈페이지에 하이퍼링크 설정
                val reservationUrlTextView = dialogBinding.findViewById<TextView>(R.id.reservationurl)
                val reservationUrl = removeHtmlTags(detail.reservationurl)
                reservationUrlTextView.text = reservationUrl
                reservationUrlTextView.autoLinkMask = Linkify.WEB_URLS
                reservationUrlTextView.movementMethod = LinkMovementMethod.getInstance()
            }
            38 -> { // 쇼핑
                dialogBinding.findViewById<TextView>(R.id.chkbabycarriageshopping).text = detail.chkbabycarriageshopping
                dialogBinding.findViewById<TextView>(R.id.chkcreditcardshopping).text = detail.chkcreditcardshopping
                dialogBinding.findViewById<TextView>(R.id.chkpetshopping).text = detail.chkpetshopping
                dialogBinding.findViewById<TextView>(R.id.culturecenter).text = detail.culturecenter
                dialogBinding.findViewById<TextView>(R.id.fairday).text = detail.fairday
                dialogBinding.findViewById<TextView>(R.id.infocentershopping).apply {
                    text = detail.infocenter
                    // 전화번호에 대한 자동 링크 설정
                    autoLinkMask = Linkify.PHONE_NUMBERS
                    movementMethod = LinkMovementMethod.getInstance()
                }
                dialogBinding.findViewById<TextView>(R.id.opendateshopping).text = detail.opendateshopping
                dialogBinding.findViewById<TextView>(R.id.opentime).text = detail.opentime
                dialogBinding.findViewById<TextView>(R.id.parkingshopping).text = detail.parkingshopping
                dialogBinding.findViewById<TextView>(R.id.restdateshopping).text = detail.restdateshopping
                dialogBinding.findViewById<TextView>(R.id.restroom).text = detail.restroom
                dialogBinding.findViewById<TextView>(R.id.saleitem).text = detail.saleitem
                dialogBinding.findViewById<TextView>(R.id.saleitemcost).text = detail.saleitemcost
                dialogBinding.findViewById<TextView>(R.id.scaleshopping).text = detail.scaleshopping
                dialogBinding.findViewById<TextView>(R.id.shopguide).text = detail.shopguide
            }
            39 -> { // 음식점
                dialogBinding.findViewById<TextView>(R.id.chkcreditcardfood).text = detail.chkcreditcardfood
                dialogBinding.findViewById<TextView>(R.id.discountinfofood).text = detail.discountinfofood
                dialogBinding.findViewById<TextView>(R.id.firstmenu).text = detail.firstmenu
                dialogBinding.findViewById<TextView>(R.id.infocenterfood).apply {
                    text = detail.infocenter
                    // 전화번호에 대한 자동 링크 설정
                    autoLinkMask = Linkify.PHONE_NUMBERS
                    movementMethod = LinkMovementMethod.getInstance()
                }
                dialogBinding.findViewById<TextView>(R.id.kidsfacility).text = if(detail.kidsfacility == "1") "O" else "X"
                dialogBinding.findViewById<TextView>(R.id.opendatefood).text = detail.opendatefood
                dialogBinding.findViewById<TextView>(R.id.opentimefood).text = detail.opentimefood
                dialogBinding.findViewById<TextView>(R.id.packing).text = detail.packing
                dialogBinding.findViewById<TextView>(R.id.parkingfood).text = detail.parkingfood
                dialogBinding.findViewById<TextView>(R.id.reservationfood).apply {
                    text = detail.infocenter
                    // 전화번호에 대한 자동 링크 설정
                    autoLinkMask = Linkify.PHONE_NUMBERS
                    movementMethod = LinkMovementMethod.getInstance()
                }
                dialogBinding.findViewById<TextView>(R.id.restdatefood).text = detail.restdatefood
                dialogBinding.findViewById<TextView>(R.id.scalefood).text = detail.scalefood
                dialogBinding.findViewById<TextView>(R.id.seat).text = detail.seat
                dialogBinding.findViewById<TextView>(R.id.smoking).text = if(detail.smoking == "1") "O" else "X"
                dialogBinding.findViewById<TextView>(R.id.treatmenu).text = detail.treatmenu
                dialogBinding.findViewById<TextView>(R.id.lcnsno).text = detail.lcnsno
            }
        }

        dialog.show()
    }

    private fun retryFetchTouristInfo(areaCode: Int, contentTypeId: Int) {
        retryCount++
        fetchTouristInfo(areaCode, contentTypeId, randomPage = true) // 재시도 시에도 랜덤 페이지로 가져오기
    }


    private fun fetchLocation(onLocationFetched: (Int) -> Unit) {
        locationService.fetchLocation { latitude, longitude ->
            activity?.runOnUiThread {
                //val tvLocation = binding.root.findViewById<TextView>(R.id.tvLocation)
                //tvLocation.text = "위도: $latitude, 경도: $longitude"
                val nearestArea = findNearestArea(latitude, longitude)
                if (nearestArea != null) {
                    currentAreaCode = nearestArea.code
                    fetchTotalPages(currentAreaCode, currentContentTypeId)
                    val displayName = if (nearestArea.name == "세종특별자치시") "세종시" else nearestArea.name
                    binding.locationButtonText.text = "현재 위치 : $displayName"
                } else {
                    Log.d("LOCATION", "Nearest area not found")
                    binding.locationButtonText.text = "현재 위치의 관광지를 추천할 수 없습니다."
                }

                val (nx, ny) = GridConverter.convertToGridCoordinates(latitude, longitude)
                Log.d("LOCATION_COORDINATES", "Latitude: $latitude, Longitude: $longitude -> NX: $nx, NY: $ny")
                fetchWeather(nx, ny, "내 위치")
            }
        }
    }

    private fun findNearestArea(latitude: Double, longitude: Double): Area? {
        return areas.minByOrNull { distanceBetween(latitude, longitude, it.latitude, it.longitude) }
    }

    private fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371e3; // metres
        val phi1 = lat1 * Math.PI / 180;
        val phi2 = lat2 * Math.PI / 180;
        val deltaPhi = (lat2 - lat1) * Math.PI / 180;
        val deltaLambda = (lon2 - lon1) * Math.PI / 180;

        val haversineA = sin(deltaPhi / 2) * sin(deltaPhi / 2) +
                cos(phi1) * cos(phi2) *
                sin(deltaLambda / 2) * sin(deltaLambda / 2);
        val centralAngle = 2 * atan2(sqrt(haversineA), sqrt(1 - haversineA));

        val distance = earthRadius * centralAngle;
        return distance;
    }

    private fun fetchWeather(nx: Int, ny: Int, city: String) {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HHmm", Locale.getDefault())
        val currentTime = Calendar.getInstance()

        // 초단기실황의 baseTime 설정
        val ultraSrtNcstBaseDate = dateFormat.format(currentTime.time)
        val ultraSrtNcstBaseTime = timeFormat.format(currentTime.time).let {
            val hour = currentTime.get(Calendar.HOUR_OF_DAY)
            val minute = currentTime.get(Calendar.MINUTE)

            if (minute >= 40) {
                String.format("%02d00", hour)
            } else {
                String.format("%02d00", if (hour == 0) 23 else hour - 1)
            }
        }

        // 단기예보의 baseTime과 baseDate 설정
        val (vilageFcstBaseTime, vilageFcstBaseDate) = getVilageFcstBaseTimeAndDate()

        var ptyValue: String? = null
        var skyValue: String? = null
        var temperature: String? = null
        var humidity: String? = null
        var popValue: String? = null

        val ultraSrtNcstUrl = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getUltraSrtNcst?serviceKey=oX0uqL6VzriCMyNDlwDB23W4%2Bb9mn8EPDaqry2QN4hO9qaQCMH5oQOhK9oIi92TiDYQ6vAY9nv9XDubAGOdugw%3D%3D&numOfRows=50&pageNo=1&dataType=XML&base_date=$ultraSrtNcstBaseDate&base_time=$ultraSrtNcstBaseTime&nx=$nx&ny=$ny"
        Log.d("WEATHER_API_ULTRA_SRT_NCST_URL", ultraSrtNcstUrl)

        val vilageFcstUrl = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst?serviceKey=oX0uqL6VzriCMyNDlwDB23W4%2Bb9mn8EPDaqry2QN4hO9qaQCMH5oQOhK9oIi92TiDYQ6vAY9nv9XDubAGOdugw%3D%3D&numOfRows=50&pageNo=1&dataType=XML&base_date=$vilageFcstBaseDate&base_time=$vilageFcstBaseTime&nx=$nx&ny=$ny"
        Log.d("WEATHER_API_VILAGE_FCST_URL", vilageFcstUrl)

        val ultraSrtNcstCall = weatherApiService.getUltraSrtNcst(ultraSrtNcstUrl)
        val vilageFcstCall = weatherApiService.getVilageFcst(vilageFcstUrl)

        ultraSrtNcstCall.enqueue(object : Callback<WeatherResponse> {
            override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                if (response.isSuccessful) {
                    Log.d("WEATHER_RESPONSE_ULTRA_SRT_NCST", response.body().toString())
                    Log.d("WEATHER_RAW_RESPONSE_ULTRA_SRT_NCST", response.raw().toString())
                    val items = response.body()?.body?.items?.item
                    temperature = items?.find { it.category == "T1H" }?.obsrValue
                    humidity = items?.find { it.category == "REH" }?.obsrValue
                    ptyValue = items?.find { it.category == "PTY" }?.obsrValue

                    updateWeather(ptyValue, skyValue, temperature, humidity, popValue, city)
                } else {
                    Log.e("WEATHER_ERROR_ULTRA_SRT_NCST", "Response code: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                Log.e("WEATHER_FAILURE_ULTRA_SRT_NCST", "Failed to fetch weather data", t)
            }
        })

        vilageFcstCall.enqueue(object : Callback<WeatherResponse> {
            override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                if (response.isSuccessful) {
                    Log.d("WEATHER_RESPONSE_VILAGE_FCST", response.body().toString())
                    val items = response.body()?.body?.items?.item
                    skyValue = items?.find { it.category == "SKY" }?.fcstValue
                    popValue = items?.find { it.category == "POP" }?.fcstValue

                    updateWeather(ptyValue, skyValue, temperature, humidity, popValue, city)
                } else {
                    Log.e("WEATHER_ERROR_VILAGE_FCST", "Response code: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                Log.e("WEATHER_FAILURE_VILAGE_FCST", "Failed to fetch short-term forecast data", t)
            }
        })
    }



    private fun getVilageFcstBaseTimeAndDate(): Pair<String, String> {
        val currentTime = Calendar.getInstance()
        val hour = currentTime.get(Calendar.HOUR_OF_DAY)
        val minute = currentTime.get(Calendar.MINUTE)
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        var baseDate: String

        val baseTime = when {
            hour < 2 || (hour == 2 && minute < 10) -> {
                baseDate = dateFormat.format(Date(currentTime.timeInMillis - 24 * 60 * 60 * 1000))
                "2300"
            }
            hour < 5 || (hour == 5 && minute < 10) -> "0200"
            hour < 8 || (hour == 8 && minute < 10) -> "0500"
            hour < 11 || (hour == 11 && minute < 10) -> "0800"
            hour < 14 || (hour == 14 && minute < 10) -> "1100"
            hour < 17 || (hour == 17 && minute < 10) -> "1400"
            hour < 20 || (hour == 20 && minute < 10) -> "1700"
            hour < 23 || (hour == 23 && minute < 10) -> "2000"
            else -> "2300"
        }

        baseDate = if (hour < 2 || (hour == 2 && minute < 10)) {
            dateFormat.format(Date(currentTime.timeInMillis - 24 * 60 * 60 * 1000))
        } else {
            dateFormat.format(currentTime.time)
        }

        return Pair(baseTime, baseDate)
    }



    private fun updateWeather(
        ptyValue: String?,
        skyValue: String?,
        temperature: String?,
        humidity: String?,
        popValue: String?,
        city: String
    ) {
        updateTemperature(temperature, city)
        updateHumidity(humidity)
        updateSky(ptyValue, skyValue)
        updateRainProbability(popValue)
    }

    private fun updateTemperature(temp: String?, city: String) {
        val tvNowCelsius = binding.root.findViewById<TextView>(R.id.tvNowCelsius)
        val tvLocation = binding.root.findViewById<TextView>(R.id.tvLocation)

        if (temp != null) {
            tvNowCelsius.text = "$temp°"
        }
        tvLocation.text = city
    }

    private fun updateHumidity(rehValue: String?) {
        val tvHumidity = binding.root.findViewById<TextView>(R.id.tvHumidity)

        if (rehValue != null) {
            tvHumidity.text = "습도: $rehValue%"
        }
    }

    private fun updateSky(ptyValue: String?, skyValue: String?) {
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
                //else -> null
                else -> {
                    imageView.setImageResource(R.drawable.weather_04)
                    "흐림"
                }
            }

            if (skyCondition != null) {
                tvSkyCondition.text = "하늘: $skyCondition"
            }
        } else {
            // If precipitation exists, update to rain icon
            imageView.setImageResource(R.drawable.weather_03)
            tvSkyCondition.text = "하늘: 비"
        }
    }

    private fun updateRainProbability(popValue: String?) {
        val tvRainProbability = binding.root.findViewById<TextView>(R.id.tvRainProbability)

        if (popValue != null) {
            tvRainProbability.text = "강수 확률: $popValue%"
        } else {
            tvRainProbability.text = "강수 확률: __%"
        }
    }

    private fun setWeatherBackground() {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val weatherLayout = binding.root.findViewById<LinearLayout>(R.id.weatherLayout) // 적절한 레이아웃 ID 사용
        val textColor: Int

        if (currentHour in 6..18) {
            weatherLayout.setBackgroundResource(R.drawable.weather_background_day)
            textColor = ContextCompat.getColor(requireContext(), android.R.color.black)
        } else {
            weatherLayout.setBackgroundResource(R.drawable.weather_background_night)
            textColor = ContextCompat.getColor(requireContext(), android.R.color.white)
        }

        updateTextColor(textColor)
    }

    private fun updateTextColor(color: Int) {
        binding.root.findViewById<TextView>(R.id.tvNowCelsius).setTextColor(color)
        binding.root.findViewById<TextView>(R.id.tvLocation).setTextColor(color)
        binding.root.findViewById<TextView>(R.id.tvSkyCondition).setTextColor(color)
        binding.root.findViewById<TextView>(R.id.tvRainProbability).setTextColor(color)
        binding.root.findViewById<TextView>(R.id.tvHumidity).setTextColor(color)
    }


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
