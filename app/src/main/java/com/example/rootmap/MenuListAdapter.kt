package com.example.rootmap

import android.content.Context
import android.content.Intent
import android.databinding.tool.util.addResource
import android.databinding.tool.util.readResources
import android.icu.number.IntegerWidth
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat.startActivity
import androidx.recyclerview.widget.RecyclerView
import com.example.rootmap.databinding.MenuItemBinding


class MenuListAdapter(): RecyclerView.Adapter<Holder>() {

    var list= mutableListOf<MenuItem>()
    //var list= mutableListOf<String>()
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding=MenuItemBinding.inflate(LayoutInflater.from(parent.context),parent,false)
        var context=parent.context
        return Holder(binding,context)
    }
    override fun getItemCount(): Int {
        return list.size
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val screen=list.get(position)
        holder.setMenu(screen)
    }

}

class Holder(val binding: MenuItemBinding,val context: Context): RecyclerView.ViewHolder(binding.root){
    init {
        /*클릭 시 발생할 이벤트 코드*/
        //메뉴 클릭시 각 항목으로 이동하는 코드 작성
        binding.root.setOnClickListener {
            var bundle = Bundle()
            when(binding.menuName.text){
                "친구"-> {val intent = Intent(context,FriendActivity::class.java)
                    startActivity(context,intent,bundle)
                }
                "가계부"-> {
                    Toast.makeText(binding.root.context, "가계부 이동하는 코드로 수정(?)", Toast.LENGTH_SHORT)
                        .show()

                }

            }

        }

    }
    fun setMenu(menu:MenuItem){
        //해당 binding화면에정보 제공
        binding.menuName.text=menu.name
        val img_res=context.resources.getIdentifier(menu.img,"drawable",context.packageName)
       binding.icon.setImageResource(img_res)

    }



}