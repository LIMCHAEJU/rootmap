package com.example.rootmap

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Toast
import android.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.rootmap.databinding.FragmentMenu2Binding
import com.example.rootmap.databinding.PopupFilterBinding
import com.example.rootmap.databinding.RecyclerviewDialogBinding
import com.example.rootmap.databinding.RouteaddLayoutBinding
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val ARG_PARAM1 = "param1_board"
private const val ARG_PARAM2 = "param2_board"
private const val PREFS_NAME = "FilterPrefs"

class MenuFragment2 : Fragment() {
    private var param1: String? = null
    private var param2: String? = null
    lateinit var binding: FragmentMenu2Binding

    private val selectedLocations = mutableListOf<String>()
    private val selectedDurations = mutableListOf<String>()
    private val selectedThemes = mutableListOf<String>()

    lateinit var routeDialog: AlertDialog
    lateinit var routelistAdapter: MyDocumentAdapter
    lateinit var routeList: MutableList<MyRouteDocument>
    lateinit var currentId:String
    lateinit var docId:String
    lateinit var docName:String
    lateinit var docOwner:String

    lateinit var postlistAdapter: RouteListAdapter
   lateinit var postLists: MutableList<RoutePost>
   lateinit var postListCopy: MutableList<RoutePost>

   lateinit var routeListDataAdapter:ListLocationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            currentId = it.getString("id").toString()
            param2 = it.getString(ARG_PARAM2)
        }
        routeList = mutableListOf<MyRouteDocument>()
        routelistAdapter= MyDocumentAdapter()
        routelistAdapter.mode="makePost"
        routelistAdapter.userId=currentId

        postLists= mutableListOf()
        postListCopy= mutableListOf()
        postlistAdapter=RouteListAdapter()
       postlistAdapter.postMode=true

        routeListDataAdapter=ListLocationAdapter()
        routeListDataAdapter.postView=true

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentMenu2Binding.inflate(inflater, container, false)
        // 필터 버튼 클릭 이벤트
        binding.filterButton.setOnClickListener {
            showFilterPopup("filter")
        }
        binding.postMyRouteButton.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                loadMyList()
                 routeDialog=makeMyPost()
            }
        }
        binding.postListView.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        CoroutineScope(Dispatchers.Main).launch {
            postlistAdapter.postList=postLists
            binding.postListView.adapter=postlistAdapter
            binding.postListView.layoutManager=LinearLayoutManager(this@MenuFragment2.context)
        }

        routelistAdapter.setItemClickListener(object: MyDocumentAdapter.OnItemClickListener{
            //리스트의 버튼 클릭시 동작
            override fun onClick(v: View, position: Int) {
                //버튼 눌렀을때의 코드
                docId = routelistAdapter.list[position].docId
                docName = routelistAdapter.list[position].docName
                docOwner = routelistAdapter.list[position].owner
                showFilterPopup("post")
            }
            override fun onListClick(v: View, position: Int) {
            }
            override fun deleteDoc(v: View, position: Int) {
            }
        })
        postlistAdapter.setItemClickListener(object: RouteListAdapter.OnItemClickListener{
            override fun onClick(v: View, position: Int) {
                //해당 경로 내의 여행지 리스트 보기
                var clickItem=postlistAdapter.postList[position]
                viewRoute(clickItem.routeName,clickItem.docId,clickItem.ownerId)
            }
            override fun onButtonClick(v: View, position: Int) {
                //다운
                Toast.makeText(this@MenuFragment2.context,"다운",Toast.LENGTH_SHORT).show()
            }

            override fun heartClick(v: View, position: Int) {
                Toast.makeText(this@MenuFragment2.context,"하트",Toast.LENGTH_SHORT).show()
            }

            override fun commentClick(v: View, position: Int) {

            }

        })
        if (container != null) {
            routeListDataAdapter.parent=container
        }

        return binding.root
    }
    override fun onResume() {
        CoroutineScope(Dispatchers.Main).launch {
            loadPostList()
            postlistAdapter.notifyDataSetChanged()
        }
        super.onResume()
    }
    private fun viewRoute(routeName:String,docId:String,ownerId:String):AlertDialog { //다이어로그로 팝업창 구현
        val dialogBinding = RecyclerviewDialogBinding.inflate(layoutInflater)
        val dialogBuild =AlertDialog.Builder(context).setView(dialogBinding.root)
        var routeData= mutableListOf<MyLocation>()
        dialogBuild.setTitle(routeName)
        CoroutineScope(Dispatchers.Main).launch {
            routeData=loadPostData(docId,ownerId)
            routeListDataAdapter.list=routeData
            dialogBinding.listView.adapter = routeListDataAdapter
        }
        dialogBinding.run{
            listView.layoutManager = LinearLayoutManager(context)
            addTripRouteText.visibility=View.INVISIBLE
            listView.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
            commentButton2.visibility=View.VISIBLE
            downloadButton.visibility=View.VISIBLE
            commentButton2.setOnClickListener {
                Toast.makeText(this@MenuFragment2.context,"댓글",Toast.LENGTH_SHORT).show()
            }
            downloadButton.setOnClickListener {
                Toast.makeText(this@MenuFragment2.context,"다운",Toast.LENGTH_SHORT).show()
            }
        }
        val dialog = dialogBuild.show()
        return dialog
    }
    private suspend fun loadPostData(docId:String,ownerId:String):MutableList<MyLocation>{
        var dataList= mutableListOf<Map<String,*>>()
        var postDatas= mutableListOf<MyLocation>()
        return try {
            var data: MutableMap<*, *>
            Firebase.firestore.collection("user").document(ownerId).collection("route").document(docId).get().addOnSuccessListener { documents->
                data=documents.data as MutableMap<*,*>
                dataList.addAll(data["routeList"] as List<Map<String,*>>)
                dataList.forEach{
                    postDatas.add(MyLocation(it["name"].toString(),it["position"] as GeoPoint,it["memo"] as String,it["spending"] as String))
                }
            }.await()
            postDatas
        } catch (e: FirebaseException) {
            Log.d("list_test", "error")
            postDatas
        }
    }

    private fun showFilterPopup(mode:String) {
        val popupBinding = PopupFilterBinding.inflate(LayoutInflater.from(context))
         val locations = mutableListOf<String>()
         val duration=mutableListOf<String>()
         val themes = mutableListOf<String>()
        if(mode=="filter"){
            // 여행지, 여행일, 테마 체크박스 동적 생성
            addCheckBoxes(R.array.locations_array, popupBinding.locationsContainer, "locations")
            addCheckBoxes(R.array.durations_array, popupBinding.durationsContainer, "durations")
            addCheckBoxes(R.array.themes_array, popupBinding.themesContainer, "themes")
        }else{
            //게시글 만들기 ver
            addCheckBoxesPostVer(R.array.locations_array, popupBinding.locationsContainer, "locations")
            addCheckBoxesPostVer(R.array.durations_array, popupBinding.durationsContainer, "durations")
            addCheckBoxesPostVer(R.array.themes_array, popupBinding.themesContainer, "themes")
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(popupBinding.root)
            .setPositiveButton("확인") { _, _ ->
                if(mode=="filter"){
                //필터버튼 사용시
                    applyFilters(popupBinding)
                }
                else{//게시글 만들때
                    checkForPost(popupBinding.locationsContainer,locations)
                    checkForPost(popupBinding.durationsContainer,duration)
                    checkForPost(popupBinding.themesContainer,themes)
                    postMyRouteDb(locations,duration,themes)
                    routeDialog.dismiss()
                }
            }
            .setNegativeButton("취소", null)
            .setNeutralButton("초기화") { _, _ ->
                resetFilters(popupBinding)
                applyFilters(popupBinding)
            }
            .create()
        dialog.show()
    }

    private fun applyFilters(popupBinding: PopupFilterBinding) {
        selectedLocations.clear()
        selectedDurations.clear()
        selectedThemes.clear()
        // 여행지 선택 확인
        checkAndAddAll(popupBinding.locationsContainer, selectedLocations, "locations")
        checkAndAddAll(popupBinding.durationsContainer, selectedDurations, "durations")
        checkAndAddAll(popupBinding.themesContainer, selectedThemes, "themes")

        updateSelectedOptions()
    }

    private fun resetFilters(popupBinding: PopupFilterBinding) {
        // 모든 체크박스 초기화
        clearAllCheckBoxes(popupBinding.locationsContainer, "locations")
        clearAllCheckBoxes(popupBinding.durationsContainer, "durations")
        clearAllCheckBoxes(popupBinding.themesContainer, "themes")
    }

    private fun postMyRouteDb(list: MutableList<String>,list2: MutableList<String>,list3: MutableList<String> ){
        var emptyList= listOf<String>()
        var uploadList=list+list2+list3
        Firebase.firestore.collection("route").document(docId).set(hashMapOf("owner" to docOwner,"tripname" to docName,"comment" to emptyList,"option" to uploadList)).addOnSuccessListener {
            Toast.makeText(this.context,"성공적으로 업로드하였습니다.",Toast.LENGTH_SHORT).show()
            CoroutineScope(Dispatchers.Main).launch {
                loadPostList()
                postlistAdapter.notifyDataSetChanged()
            }
        }
    }
    private fun checkForPost(container: ViewGroup, list: MutableList<String>){
        for (i in 0 until container.childCount) {
            val checkBox = container.getChildAt(i) as CheckBox
            checkAndAdd(checkBox, list)
        }
    }

    private fun clearAllCheckBoxes(container: ViewGroup, keyPrefix: String) {
        for (i in 0 until container.childCount) {
            val checkBox = container.getChildAt(i) as CheckBox
            checkBox.isChecked = false
            saveCheckboxState("$keyPrefix$i", false)
        }
    }

    private fun checkAndAddAll(container: ViewGroup, list: MutableList<String>, keyPrefix: String) {
        for (i in 0 until container.childCount) {
            val checkBox = container.getChildAt(i) as CheckBox
            checkAndAdd(checkBox, list)
            saveCheckboxState("$keyPrefix$i", checkBox.isChecked)
        }
    }

    private fun checkAndAdd(checkBox: CheckBox, list: MutableList<String>) {
        if (checkBox.isChecked) {
            list.add(checkBox.text.toString())
        }
    }

    private fun updateSelectedOptions() {
        val selectedOptions = "여행지: ${selectedLocations.joinToString(", ")}\n여행일: ${selectedDurations.joinToString(", ")}\n테마: ${selectedThemes.joinToString(", ")}"
        binding.selectedOptionsTextView.text = selectedOptions
        filter()
    }

    private fun filter(){
        var filterResult= listOf<RoutePost>()
        val selectedOptions=selectedLocations+selectedDurations+selectedThemes

        if(selectedOptions.isEmpty()){
            //초기화
            postLists.clear()
            postLists.addAll(postListCopy)
            postlistAdapter.notifyDataSetChanged()
        }else{
            //필터 적용
            filterResult=postLists.filter {
                it.option.containsAll(selectedOptions)
            }
            postLists.clear()
            postLists.addAll(filterResult)
            postlistAdapter.notifyDataSetChanged()
        }
        Log.d("filter_test",postListCopy.size.toString())
    }

    private fun addCheckBoxes(arrayResId: Int, container: ViewGroup, keyPrefix: String) {
        val items = resources.getStringArray(arrayResId)
        val sharedPrefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        for (i in items.indices) {
            val checkBox = CheckBox(context).apply {
                text = items[i]
                isChecked = sharedPrefs.getBoolean("$keyPrefix$i", false)
            }
            container.addView(checkBox)
        }
    }

    private fun addCheckBoxesPostVer(arrayResId: Int, container: ViewGroup, keyPrefix: String) {
        val items = resources.getStringArray(arrayResId)
        val sharedPrefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        for (i in items.indices) {
            val checkBox = CheckBox(context).apply {
                text = items[i]
            }
            container.addView(checkBox)
        }
    }


    private fun saveCheckboxState(key: String, state: Boolean) {
        val sharedPrefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putBoolean(key, state)
            apply()
        }
    }
    private fun makeMyPost():AlertDialog { //다이어로그로 팝업창 구현
        val dialogBinding = RecyclerviewDialogBinding.inflate(layoutInflater)
        val dialogBuild = android.app.AlertDialog.Builder(context).setView(dialogBinding.root)
        dialogBuild.setTitle("내가 만든 여행 리스트")
        routelistAdapter.list=routeList
        dialogBinding.listView.adapter = routelistAdapter
        dialogBinding.listView.layoutManager = LinearLayoutManager(context)
        if(routelistAdapter.list.isNullOrEmpty()){
            dialogBinding.checkText.apply {
                text="아직 경로가 없습니다. 새로운 경로를 만들어주세요."
                visibility=View.VISIBLE
            }
        }
        dialogBinding.addTripRouteText.visibility=View.GONE
        routeDialog = dialogBuild.show()
        return routeDialog
    }
    private suspend fun loadMyList(): Boolean {
        routeList.clear()
        return try {
            val myList = MenuFragment3.getInstance()!!.returnDb(currentId).get().await()
            if(!myList.isEmpty){
                myList.forEach {
                    routeList.add(MyRouteDocument(it.data?.get("tripname").toString(),it.id,currentId))
                }
            }
            true
        } catch (e: FirebaseException) {
            Log.d("list_test", "error")
            false
        }
    }

    private suspend fun loadPostList(): Boolean {
        postLists.clear()
        return try {
            val postListData=Firebase.firestore.collection("route").get().await()
            if(!postListData.isEmpty){
                postListData.forEach {
                    //임시로 일단 좋아요 수는 0
                    val user=it.data?.get("owner").toString()
                    val data=RoutePost(it.data?.get("tripname").toString(),0,it.id,user,loadUserName(user),it.data?.get("option") as List<String>)
                    postLists.add(data)
                    postListCopy.add(data)
                }
            }
            true
        } catch (e: FirebaseException) {
            Log.d("list_test", "error")
            false
        }
    }
    private suspend fun loadUserName(id:String):String{
        return try {
            val nickname=Firebase.firestore.collection("user").document(id).get().await().get("nickname").toString()
            return nickname
        } catch (e: FirebaseException) {
            Log.d("list_test", "error")
            return "error"
        }
    }
    private fun loadLike(docId:String){
        //일단 보류

    }


    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            MenuFragment2().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}
