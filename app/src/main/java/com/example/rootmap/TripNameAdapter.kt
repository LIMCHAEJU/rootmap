package com.example.rootmap

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TripNameAdapter(private val tripNames: List<String>, private val context: Context, private val userEmail: String) :
    RecyclerView.Adapter<TripNameAdapter.TripNameViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripNameViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_trip_name, parent, false)
        return TripNameViewHolder(view, context, userEmail)
    }

    override fun onBindViewHolder(holder: TripNameViewHolder, position: Int) {
        holder.bind(tripNames[position])
    }

    override fun getItemCount(): Int {
        return tripNames.size
    }

    class TripNameViewHolder(itemView: View, private val context: Context, private val userEmail: String) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.tripNameTextView)
        private val button: Button = itemView.findViewById(R.id.tripDetailButton)

        fun bind(tripname: String) {
            textView.text = tripname
            button.setOnClickListener {
                val intent = Intent(context, ExpenditureDetailActivity::class.java)
                intent.putExtra("tripname", tripname)
                intent.putExtra("userEmail", userEmail)
                context.startActivity(intent)
            }
        }
    }
}
