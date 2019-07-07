package com.stasbar.pdfscraper

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class UriAdapter(val onClick : (String) -> Unit) : RecyclerView.Adapter<UriAdapter.ViewHolder>() {
    var dataSet = mutableListOf<String>()

    fun replaceData(newValues: List<String>) {
        dataSet.clear()
        dataSet.addAll(newValues)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, null) as TextView
        return ViewHolder(view)
    }

    override fun getItemCount() = dataSet.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = dataSet[position]
        holder.textView.setOnClickListener {
            onClick(dataSet[position])
        }
    }

    class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
}