package com.example.rootmap

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.Friend
import com.example.rootmap.databinding.FragmentFriendListBinding

//친구 리스트 프래그먼트-현재 자신과 친구 상태인 유저의 리스트를 출력 등의 기능을 가진 화면
// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [FriendList.newInstance] factory method to
 * create an instance of this fragment.
 */
class FriendList<MutabelList> : Fragment() {
    // TODO: Rename and change types of parameters
    private var currentId: String? = null
    private var param2: String? = null
    //프래그먼트의 binding
    lateinit var binding:FragmentFriendListBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            currentId = it.getString("id")
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        //binding 지정
        binding= FragmentFriendListBinding.inflate(inflater, container, false)

        //여기부터 코드 작성
        //Toast.makeText(context,currendId, Toast.LENGTH_SHORT).show()
        var adapter=FriendAdapter("List",currentId.toString())
        binding.recyclerList.adapter=adapter
        binding.recyclerList.layoutManager= LinearLayoutManager(activity)

        return binding.root
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment FriendList.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            FriendList<Any>().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }


}