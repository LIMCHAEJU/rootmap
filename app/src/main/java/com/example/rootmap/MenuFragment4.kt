package com.example.rootmap

import android.R
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.Color
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.rootmap.databinding.DialogLayoutBinding
import com.example.rootmap.databinding.FragmentMenu4Binding
import com.example.rootmap.databinding.DialogChangePasswordBinding
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.async
import kotlinx.coroutines.tasks.await
import android.Manifest
import android.app.Activity.RESULT_OK
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import java.io.File
import android.net.Uri
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.storage.FirebaseStorage
import com.navercorp.nid.NaverIdLoginSDK
import com.navercorp.nid.oauth.NidOAuthLogin
import com.navercorp.nid.oauth.OAuthLoginCallback

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [MenuFragment4.newInstance] factory method to
 * create an instance of this fragment.
 */
class MenuFragment4 : Fragment() {
    // TODO: Rename and change types of parameters
    private var id: String? = null
    private var param2: String? = null
    private lateinit var auth: FirebaseAuth

    //프래그먼트의 binding
    val binding by lazy { FragmentMenu4Binding.inflate(layoutInflater) }
    lateinit var name: String
    lateinit var nickname: String
    lateinit var emailInfo: String
    lateinit var storagePermission:ActivityResultLauncher<String>
    lateinit var galleryLauncher: ActivityResultLauncher<String>
    var fdStrage:FirebaseStorage=FirebaseStorage.getInstance()
    var changePhotoUri:Uri?=null
    var enableTouch=false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            id = it.getString("id")
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        //여기부터 코드 작성
        auth = FirebaseAuth.getInstance()

        var id = id.toString()
        binding.userId.text = id
        viewLifecycleOwner.lifecycleScope.async {
            loadMyData(id)
            binding.userName.text = name
            binding.userNickname.text = nickname
            binding.facebookImage.visibility =
                if (emailInfo == "페이스북") View.VISIBLE else View.GONE
            binding.googleImage.visibility =
                if (emailInfo == "구글") View.VISIBLE else View.GONE
        }
        binding.MyRouteButton.setOnClickListener {
            val intent = Intent(requireContext(), MyRouteActivity::class.java)
            startActivity(intent)
        }
        binding.MyExpenditureButton.setOnClickListener {
            val expenditureIntent = Intent(this@MenuFragment4.context, ExpenditureActivity::class.java)
            startActivity(expenditureIntent)
        }
        binding.MyFriendButton.setOnClickListener {
            val friendIntent = Intent(this@MenuFragment4.context, FriendActivity::class.java)
            friendIntent.putExtra("id", id)
            startActivity(friendIntent)
        }
        binding.favoriteTouristSpotsButton.setOnClickListener {
            val intent = Intent(requireContext(), FavoriteTouristSpotsActivity::class.java)
            startActivity(intent)
        }
        binding.favoritePostsButton.setOnClickListener {
            val intent = Intent(requireContext(), LikedPostsActivity::class.java)
            startActivity(intent)
        }

        binding.logoutButton.setOnClickListener {
            showDialog("logout")
        }
        binding.secessionButton.setOnClickListener {
            showDialog("secession")
        }
        binding.passwordButton.setOnClickListener {
            showChangePasswordDialog("changePassword")
        }
        //소셜 로그인이 아닌 이메일 로그인 시에만 비밀번호 변경 버튼 표시
        val isEmailSignIn = auth.currentUser?.providerData?.any {it.providerId == "password"} == true
        binding.passwordButton.isVisible = isEmailSignIn

        binding.icon.setOnClickListener {
            if(enableTouch){
                storagePermission.launch(Manifest.permission.READ_MEDIA_IMAGES)
            }
        }
        var fileName=id.replace(".","")
        viewLifecycleOwner.lifecycleScope.async {
            loadImg(fileName)
        }
        galleryLauncher=registerForActivityResult(ActivityResultContracts.GetContent()){uri->
            binding.icon.setImageURI(uri)
            changePhotoUri=uri
        }
        storagePermission=registerForActivityResult(ActivityResultContracts.RequestPermission()){ isGranted->
            if (isGranted){
                openGallery()
            }else{
                Toast.makeText(context,"이미지 권한이 필요한 작업입니다.",Toast.LENGTH_SHORT).show()
            }
        }
        binding.imageButton.setOnClickListener {
            binding.imageButton.visibility = View.INVISIBLE
            binding.saveButton.visibility = View.VISIBLE
            binding.userNickname.visibility = View.GONE
            binding.nickChange.visibility = View.VISIBLE
            binding.userName.visibility = View.GONE
            binding.nameChange.visibility = View.VISIBLE

            enableTouch=true

            binding.nickChange.setText(nickname)
            binding.nameChange.setText(name)

            //저장
            binding.saveButton.setOnClickListener {
                var nameText = binding.nameChange.text.toString()
                var nickText = binding.nickChange.text.toString()
                Firebase.firestore.collection("user").document(id)
                    .update(mapOf<String, String>("name" to nameText, "nickname" to nickText))
                binding.imageButton.visibility = View.VISIBLE
                binding.saveButton.visibility = View.INVISIBLE
                binding.userNickname.visibility = View.VISIBLE
                binding.nickChange.visibility = View.GONE
                binding.userName.visibility = View.VISIBLE
                binding.nameChange.visibility = View.GONE

                //프로필 이미지 변경 저장
                if(changePhotoUri!=null){
                    fdStrage.reference.child("profile").child("${fileName}.png").putFile(changePhotoUri!!)
                }
                enableTouch=false
                context?.hideKeyboard(binding.root)
                viewLifecycleOwner.lifecycleScope.async {
                    loadMyData(id)
                    binding.userName.text = name
                    binding.userNickname.text = nickname

                }
            }
        }
        //
        return binding.root
    }

    fun Context.hideKeyboard(view: View) {
        val inputMethodManager =
            getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    fun openGallery(){
        galleryLauncher.launch("image/*")
    }

    suspend fun loadMyData(id: String): Boolean {
        return try {
            val mydb = Firebase.firestore.collection("user").document(id).get().await()
            name = mydb.data?.get("name").toString()
            nickname = mydb.data?.get("nickname").toString()
            emailInfo = mydb.data?.get("emailInfo").toString()
            true
        } catch (e: FirebaseException) {
            Log.d("load_error", "error")
            false
        }
    }
    suspend fun loadImg(id:String):Boolean{
        return try {
            fdStrage.reference.child("profile/${id}.png").downloadUrl.addOnSuccessListener {uri->
                Glide.with(this).load(uri).into(binding.icon)
            }.await()
            true
        } catch (e: FirebaseException) {
            Log.d("img_error", "error")
            //  photoUri=null
            false
        }
    }

    fun showDialog(mode: String) {
        val dBinding = DialogLayoutBinding.inflate(layoutInflater)
        dBinding.wButton.text = "아니요" //다이어로그의 텍스트 변경
        dBinding.bButton.text = "네"
        if (mode == "logout") {
            dBinding.content.text = "로그아웃하시겠습니까?"
        } else {
            dBinding.content.text = "정말로 탈퇴하시겠습니까?"
            dBinding.content.setTypeface(Typeface.DEFAULT_BOLD)
            dBinding.content.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.holo_red_dark
                )
            )
        }
        val dialogBuild = AlertDialog.Builder(context).setView(dBinding.root)
        val dialog = dialogBuild.show() //다이어로그 창 띄우기
      //  dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dBinding.bButton.setOnClickListener {//다이어로그 기능 설정
            if (mode == "logout") {
                auth.signOut() // 로그아웃 처리
                val intent = Intent(this.context, LoginActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent) // LoginActivity로 화면 전환
            } else {
                secession(id.toString()) //탈퇴 후 로그인 화면으로 이동
                /*val intent = Intent(this.context, LoginActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)*/
            }
            dialog.dismiss()
        }
        dBinding.wButton.setOnClickListener {//취소버튼
            //회색 버튼의 기능 구현 ↓
            dialog.dismiss()
        }
    }

    private fun secession(id: String) {
        val db = Firebase.firestore.collection("user")
        auth = FirebaseAuth.getInstance()

        db.document(id).collection("friend").get().addOnSuccessListener {
            for (user in it) {
                db.document(user.id).collection("friend").document(id).delete()
                db.document(id).collection("friend").document(user.id).delete()
            }
        }.addOnFailureListener {
            Log.d("error", "secession error")
        }

        db.document(id).delete()
            .addOnSuccessListener {
                Log.d("Delete", "DocumentSnapshot successfully deleted!")
                val intent = Intent(this.context, LoginActivity::class.java)
                //val intent = Intent(requireContext(), LoginActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)

                // 추가 : Save user state to SharedPreferences
                val sharedPreferences = requireContext().getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
                with(sharedPreferences.edit()) {
                    putBoolean("is_deleted", true)
                    apply()
                }
            }
            .addOnFailureListener { e ->
                Log.w("Delete", "Error deleting document", e)
                Toast.makeText(context, "Error deleting document: ${e.message}", Toast.LENGTH_SHORT).show()
            }

        //유저 정보 auth에서 삭제
        val user = auth.currentUser
        user?.let {
            // 사용자 계정 삭제
            user.delete().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    //Toast.makeText(context, "사용자 계정 삭제 성공", Toast.LENGTH_SHORT).show()
                    Log.d("Delete", "User account deleted.")
                } else {
                    //Toast.makeText(context, "사용자 계정 삭제 오류", Toast.LENGTH_SHORT).show()
                    Log.w("Delete", "Error deleting user account", task.exception)
                }
            }
        }
        /*if (user != null) {
            user.delete()
        }*/
    }

    fun showChangePasswordDialog(mode: String) {
        val dcpBinding = DialogChangePasswordBinding.inflate(layoutInflater)
        dcpBinding.confirmButton.text = "변경"
        dcpBinding.cancelButton.text = "취소"

        val dialogBuildCP = AlertDialog.Builder(context).setView(dcpBinding.root).setTitle("비밀번호 변경")
        val dialogCP = dialogBuildCP.show() //다이어로그 창 띄우기

        dcpBinding.confirmButton.setOnClickListener {//다이어로그 기능 설정
            if (mode == "changePassword") {
                val currentPassword = dcpBinding.currentPassword.text.toString()
                val newPassword = dcpBinding.newPassword.text.toString()
                val confirmNewPassword = dcpBinding.confirmNewPassword.text.toString()
                val user = auth.currentUser

                if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmNewPassword.isEmpty() ||
                    currentPassword.length <= 5 || newPassword.length <= 5 || confirmNewPassword.length <= 5) {
                    Toast.makeText(context, "비밀번호는 6글자 이상입니다.", Toast.LENGTH_SHORT).show()
                } else if (user != null && user.email != null) {
                    val credential = EmailAuthProvider.getCredential(user.email!!, currentPassword)
                    user.reauthenticate(credential).addOnCompleteListener { reauthTask ->
                        if (reauthTask.isSuccessful) {
                            if (newPassword != confirmNewPassword) {
                                Toast.makeText(context, "새 비밀번호가 일치하지 않습니다", Toast.LENGTH_SHORT).show()
                            } else {
                                user.updatePassword(newPassword).addOnCompleteListener { updateTask ->
                                    if (updateTask.isSuccessful) {
                                        Toast.makeText(context, "비밀번호가 성공적으로 변경되었습니다", Toast.LENGTH_SHORT).show()
                                        dialogCP.dismiss()
                                    } else {
                                        Toast.makeText(context, "다시 시도해주세요.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } else {
                            Toast.makeText(context, "현재 비밀번호가 일치하지 않습니다", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        dcpBinding.cancelButton.setOnClickListener {
            dialogCP.dismiss()
        }
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment MenuFragment4.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            MenuFragment4().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}