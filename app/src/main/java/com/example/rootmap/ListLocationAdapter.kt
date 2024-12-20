package com.example.rootmap

import android.app.AlertDialog
import android.graphics.Canvas
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE
import androidx.recyclerview.widget.ItemTouchHelper.DOWN
import androidx.recyclerview.widget.ItemTouchHelper.LEFT
import androidx.recyclerview.widget.ItemTouchHelper.RIGHT
import androidx.recyclerview.widget.ItemTouchHelper.UP
import androidx.recyclerview.widget.RecyclerView
import com.example.rootmap.databinding.FriendLayoutBinding
import com.example.rootmap.databinding.LocationListLayoutBinding
import com.example.rootmap.databinding.MemoEditLayoutBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.firestore
import java.util.Collections
import kotlin.math.min

class ListLocationAdapter : RecyclerView.Adapter<ListLocationAdapter.Holder>()  {
    var list = mutableListOf<MyLocation>()
    lateinit var parent: ViewGroup
    lateinit var myDb: CollectionReference
    lateinit var docId: String

    var postView = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        this.parent = parent
        // Firebase Firestore 초기화 (필요한 Collection의 참조로 초기화)
        myDb = Firebase.firestore.collection("myCollection") // 원하는 Firebase collection으로 변경
        val binding =
            LocationListLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }
    override fun onBindViewHolder(holder: Holder, position: Int) {
        var screen = list.get(position)
        holder.setData(screen,position)
    }

    override fun getItemCount(): Int {
        return list.size
    }
    fun removeData(position: Int) {
        list.removeAt(position)
        notifyItemRemoved(position)
    }
    fun swapData(fromPos: Int, toPos: Int) {
        Collections.swap(list, fromPos, toPos)
        notifyItemMoved(fromPos, toPos)
    }
    inner class Holder(
        val binding: LocationListLayoutBinding
    ) :
        RecyclerView.ViewHolder(binding.root) {
        //  fun bind

        fun setData(myLocation: MyLocation,position: Int) {
            var memo=myLocation.memo
            var spending=myLocation.spending
            var day = myLocation.day
            binding.triplocationName.text=myLocation.name
            binding.memoText.text=memo
            binding.costText.text=spending

            if(postView) binding.textViewOptions.visibility=View.GONE

            binding.textViewOptions.setOnClickListener {
                val popup = PopupMenu(binding.textViewOptions.context, binding.textViewOptions)
                popup.inflate(R.menu.recyclerview_item_menu)
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.memo -> { //메모 클릭
                            showMemoDialog(memo,position)
                        }
                        R.id.spending -> { //금액 클릭
                            showSpendingDialog(spending, position)
                        }
                        R.id.day -> {
                            showSpendingDialog(day, position)
                        }
                    }
                    true
                }
                popup.show()
            }
            binding.tvRemove2.setOnClickListener {
                removeData(this.layoutPosition)
            }
        }
        private fun showMemoDialog(memo:String,position: Int): AlertDialog { //다이어로그로 팝업창 구현
            val dBinding = MemoEditLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            val dialogBuild = AlertDialog.Builder(parent.context).setView(dBinding.root)
            dialogBuild.setTitle("메모")
            dBinding.apply {
                memoCancleButton.text="닫기"
                memoSaveButton.text="확인"
                if(memo!=""){
                    memoArea.setText(memo)
                }
            }
            val dialog = dialogBuild.show()
            dBinding.apply {
                memoSaveButton.setOnClickListener {
                    //확인 버튼 클릭
                    //저장 기능
                    var text=memoArea.text.toString()
                    if(memo!=text){ //내용 수정이 된 경우
                        list[layoutPosition].memo=text
                        notifyDataSetChanged()
                    }
                    dialog.dismiss()
                }
            }
            dBinding.memoCancleButton.setOnClickListener {
                //닫기
                dialog.dismiss()
            }
            return dialog
        }

        private fun showSpendingDialog(spending: String, position: Int) {
            try {
                val dialogView = LayoutInflater.from(parent.context).inflate(R.layout.dialog_edit_spending, parent, false)
                val spendingEditText: EditText = dialogView.findViewById(R.id.editSpending)
                val expenseCategorySpinner: Spinner = dialogView.findViewById(R.id.categorySpinner)

                // 기존 지출 금액 설정
                spendingEditText.setText(spending)

                // Spinner 어댑터 설정 (카테고리 선택)
                val spinnerAdapter = ArrayAdapter.createFromResource(
                    parent.context,
                    R.array.expense_categories,
                    android.R.layout.simple_spinner_item
                )
                spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                expenseCategorySpinner.adapter = spinnerAdapter

                // Firestore에서 가져온 카테고리 값을 Spinner에 설정
                val categories = parent.context.resources.getStringArray(R.array.expense_categories)
                val currentExpense = list[position] // 현재 선택한 항목

                // 카테고리가 비어있다면 기본값을 '기타'로 설정
                val category = if (currentExpense.category.isNullOrEmpty()) "기타" else currentExpense.category
                val categoryPosition = categories.indexOf(category)
                if (categoryPosition >= 0) {
                    expenseCategorySpinner.setSelection(categoryPosition)
                }

                val dialog = AlertDialog.Builder(parent.context)
                    .setTitle("금액 및 카테고리 수정")
                    .setView(dialogView)
                    .setPositiveButton("확인") { _, _ ->
                        // 지출 금액 수정
                        val newSpending = spendingEditText.text.toString()
                        val oldSpending = currentExpense.spending
                        if (newSpending != oldSpending) {
                            currentExpense.spending = newSpending
                            notifyItemChanged(position)
                        }

                        // 카테고리 수정
                        val selectedCategory = expenseCategorySpinner.selectedItem.toString()
                        if (currentExpense.category != selectedCategory) {
                            currentExpense.category = selectedCategory
                            notifyItemChanged(position)
                        }

                        // Firestore에 지출 금액과 카테고리 업데이트
                        updateFirestore(currentExpense.name, newSpending, selectedCategory)
                    }
                    .setNegativeButton("취소", null)
                    .create()

                dialog.show()
            } catch (e: Exception) {
                Log.e("ListLocationAdapter", "Error showing spending dialog", e)
                Toast.makeText(parent.context, "Error showing spending dialog", Toast.LENGTH_SHORT).show()
            }
        }

        private fun updateFirestore(name: String, newSpending: String, newCategory: String) {
            if (!::myDb.isInitialized) {
                Log.e("ListLocationAdapter", "myDb is not initialized")
                return
            }

            myDb.document(docId).get().addOnSuccessListener { document ->
                val routeList = document.get("routeList") as? MutableList<Map<String, Any>>
                if (routeList != null) {
                    val mutableRouteList = routeList.toMutableList()
                    for (item in mutableRouteList) {
                        if (item["name"] == name) {
                            val mutableItem = item.toMutableMap()
                            mutableItem["spending"] = newSpending
                            mutableItem["category"] = newCategory
                            mutableRouteList[mutableRouteList.indexOf(item)] = mutableItem
                            break
                        }
                    }
                    // Firebase Firestore에 업데이트 수행
                    myDb.document(docId).update("routeList", mutableRouteList)
                        .addOnSuccessListener {
                            Log.d("FirestoreUpdate", "Document successfully updated!")
                        }
                        .addOnFailureListener { e ->
                            Log.e("FirestoreUpdate", "Error updating document", e)
                        }
                } else {
                    Log.e("FirestoreUpdate", "routeList is null or empty")
                }
            }.addOnFailureListener { e ->
                Log.e("FirestoreUpdate", "Error getting document", e)
            }
        }


        /*
        private fun showSpendingDialog(spending: String, position: Int) {
            try {
                val dialogView = LayoutInflater.from(parent.context).inflate(R.layout.dialog_edit_spending, parent, false)
                val spendingEditText: EditText = dialogView.findViewById(R.id.editSpending)
                spendingEditText.setText(spending)

                val dialog = AlertDialog.Builder(parent.context)
                    .setTitle("금액 수정")
                    .setView(dialogView)
                    .setPositiveButton("저장") { _, _ ->
                        val newSpending = spendingEditText.text.toString()
                        if (spending != newSpending) {
                            list[position].spending = newSpending
                            notifyDataSetChanged()
                            // updateFirestoreSpending(list[position].name, newSpending)
                        }
                    }
                    .setNegativeButton("취소", null)
                    .create()
                dialog.show()
            } catch (e: Exception) {
                Log.e("ListLocationAdapter", "Error showing spending dialog", e)
                Toast.makeText(parent.context, "Error showing spending dialog", Toast.LENGTH_SHORT).show()
            }
        }*/
    }
    // 현재 선택된 데이터와 드래그한 위치에 있는 데이터를 교환

    interface OnItemClickListener {
        fun onClick(v: View, position: Int, textViewOptions: TextView)
    }
    fun setItemClickListener(onItemClickListener: OnItemClickListener) {
        this.itemClickListener = onItemClickListener
    }

    private lateinit var itemClickListener : OnItemClickListener
}
class DragManageAdapter(private var recyclerViewAdapter : ListLocationAdapter) : ItemTouchHelper.Callback() {
    private var currentPosition: Int? = null    // 현재 선택된 recycler view의 position
    private var previousPosition: Int? = null   // 이전에 선택했던 recycler view의 position
    private var currentDx = 0f                  // 현재 x 값
    private var clamp = 0f                      // 고정시킬 크기

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        return makeMovementFlags(UP or DOWN,LEFT or RIGHT)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val fromPos: Int = viewHolder.getAbsoluteAdapterPosition()
        val toPos: Int = target.getAbsoluteAdapterPosition()
        recyclerViewAdapter.swapData(fromPos, toPos)
        return true
    }
    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        currentDx = 0f                                      // 현재 x 위치 초기화
        previousPosition = viewHolder.getAbsoluteAdapterPosition()       // 드래그 또는 스와이프 동작이 끝난 view의 position 기억하기
        getDefaultUIUtil().clearView(getView(viewHolder))
    }

    // ItemTouchHelper가 ViewHolder를 스와이프 되었거나 드래그 되었을 때 호출
    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        viewHolder?.let {
            currentPosition = viewHolder.getAbsoluteAdapterPosition()    // 현재 드래그 또는 스와이프 중인 view 의 position 기억하기
            getDefaultUIUtil().onSelected(getView(it))

        }
    }

    // 아이템을 터치하거나 스와이프하는 등 뷰에 변화가 생길 경우 호출
    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (actionState == ACTION_STATE_SWIPE) {
            val view = getView(viewHolder)
            val isClamped = getTag(viewHolder)      // 고정할지 말지 결정, true : 고정함 false : 고정 안 함
            val newX = clampViewPositionHorizontal(dX, isClamped, isCurrentlyActive)  // newX 만큼 이동(고정 시 이동 위치/고정 해제 시 이동 위치 결정)
            // 고정시킬 시 애니메이션 추가
            if (newX == -clamp) {
                view.animate().translationX(-clamp).setDuration(100L).start()
                return
            }
            currentDx = newX
            getDefaultUIUtil().onDraw(
                c,
                recyclerView,
                view,
                newX,
                dY,
                actionState,
                isCurrentlyActive
            )
        }
    }

    // 사용자가 view를 swipe 했다고 간주할 최소 속도 정하기
    override fun getSwipeEscapeVelocity(defaultValue: Float): Float = defaultValue * 10

    // 사용자가 view를 swipe 했다고 간주하기 위해 이동해야하는 부분 반환
    // (사용자가 손을 떼면 호출됨)
    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
        // -clamp 이상 swipe시 isClamped를 true로 변경 아닐시 false로 변경
        setTag(viewHolder, currentDx <= -clamp)
        return 2f
    }

    // swipe_view 반환 -> swipe_view만 이동할 수 있게 해줌
    private fun getView(viewHolder: RecyclerView.ViewHolder) : View = viewHolder.itemView.findViewById(R.id.swipe_view)

    // swipe_view 를 swipe 했을 때 <삭제> 화면이 보이도록 고정
    private fun clampViewPositionHorizontal(
        dX: Float,
        isClamped: Boolean,
        isCurrentlyActive: Boolean
    ) : Float {
        // RIGHT 방향으로 swipe 막기
        val max = 0f

        // 고정할 수 있으면
        val newX = if (isClamped) {
            // 현재 swipe 중이면 swipe되는 영역 제한
            if (isCurrentlyActive){
                // 오른쪽 swipe일 때
                if (dX < 0){
                    dX/3 - clamp
                }else dX - clamp // 왼쪽 swipe일 때
            }else -clamp// swipe 중이 아니면 고정시키기
        }
        // 고정할 수 없으면 newX는 스와이프한 만큼
        else dX / 2

        // newX가 0보다 작은지 확인
        return min(newX, max)
    }

    // isClamped를 view의 tag로 관리
    // isClamped = true : 고정, false : 고정 해제
    private fun setTag(viewHolder: RecyclerView.ViewHolder, isClamped: Boolean) { viewHolder.itemView.tag = isClamped }
    private fun getTag(viewHolder: RecyclerView.ViewHolder) : Boolean =  viewHolder.itemView.tag as? Boolean ?: false


    // view가 swipe 되었을 때 고정될 크기 설정
    fun setClamp(clamp: Float) { this.clamp = clamp }

    // 다른 View가 swipe 되거나 터치되면 고정 해제
    fun removePreviousClamp(recyclerView: RecyclerView) {
        // 현재 선택한 view가 이전에 선택한 view와 같으면 패스
        if (currentPosition == previousPosition) return

        // 이전에 선택한 위치의 view 고정 해제
        previousPosition?.let {
            val viewHolder = recyclerView.findViewHolderForAdapterPosition(it) ?: return
            getView(viewHolder).animate().x(0f).setDuration(100L).start()
            setTag(viewHolder, false)
            previousPosition = null
        }

    }


}