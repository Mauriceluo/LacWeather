package com.example.xiyou3g.lacweather.fragment

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.view.GravityCompat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

import com.example.xiyou3g.lacweather.R
import com.example.xiyou3g.lacweather.activity.MainActivity
import com.example.xiyou3g.lacweather.activity.WeatherActivity
import com.example.xiyou3g.lacweather.db.City
import com.example.xiyou3g.lacweather.db.County
import com.example.xiyou3g.lacweather.db.Province
import com.example.xiyou3g.lacweather.util.HttpUtil
import com.example.xiyou3g.lacweather.util.LogUtil
import com.example.xiyou3g.lacweather.util.Utility
import kotlinx.android.synthetic.main.activity_weather.*

import org.litepal.crud.DataSupport

import java.io.IOException
import java.util.ArrayList

import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.jetbrains.anko.support.v4.drawerLayout

/**
 * Created by Lance on 2017/8/16.
 */

class ChooseAreaFragment : Fragment() {
    private var progressDialog: ProgressDialog? = null
    private var titleText: TextView? = null
    private var backButton: Button? = null
    private var listView: ListView? = null
    private var adapter: ArrayAdapter<String>? = null
    private val dataList = ArrayList<String>()

    /*省列表*/
    private var provinceList: List<Province>? = null
    /*市列表*/
    private var cityList: List<City>? = null
    /*县列表*/
    private var countyList: List<County>? = null

    /*选中的省份*/
    private var selectedProvince: Province? = null
    /*选中的城市*/
    private var selectedCity: City? = null
    /*当前选中的级别*/
    private var currentLevel: Int = 0

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater!!.inflate(R.layout.choose_area, container, false)
        titleText = view.findViewById(R.id.title_text) as TextView
        backButton = view.findViewById(R.id.back_button) as Button
        listView = view.findViewById(R.id.list_view) as ListView
        adapter = ArrayAdapter(context, android.R.layout.simple_expandable_list_item_1, dataList)
        listView!!.adapter = adapter
        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        listView!!.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
            if (currentLevel == LEVEL_PROVINCE) {
                selectedProvince = provinceList!![position]
//                LogUtil.e("position",position.toString())
//                LogUtil.e("selectProvince",selectedProvince!!.provinceName.toString())
//                LogUtil.e("selectProvince",selectedProvince!!.provinceCode.toString())
                queryCities()
            } else if (currentLevel == LEVEL_CITY) {
                selectedCity = cityList!![position]
//                LogUtil.e("selectCity",selectedCity!!.cityCode.toString())
                queryCounties()
            }else if(currentLevel == LEVEL_COUNTY){
                val stringId = countyList!![position].weatherId
                if(activity is MainActivity){
                    val intent = Intent(activity,WeatherActivity::class.java)
                    intent.putExtra("weather_id",stringId)
                    startActivity(intent)
                    activity.finish()
                }else if(activity is WeatherActivity) {
//                    val activity1 = activity@(WeatherActivity)
//                    activity.drawerLayout!!.closeDrawer(GravityCompat.END)
//                    activity.swipeRefresh!!.isRefreshing = true
//                    activity.requestWeather(stringId)
                    val intent = Intent(activity,WeatherActivity::class.java)
                    intent.putExtra("weather_id",stringId)
                    intent.putExtra("nav_change",1)
                    LogUtil.e("change position",countyList!![position].weatherId.toString())
                    startActivity(intent)
                    activity.finish()
                }
            }
        }

        backButton!!.setOnClickListener {
            if (currentLevel == LEVEL_COUNTY) {
                queryCities()
            } else if (currentLevel == LEVEL_CITY) {
                queryProvinces()
            }
        }
        queryProvinces()
    }

    private fun queryProvinces() {
        titleText!!.text = "中国"
        backButton!!.visibility = View.GONE
        provinceList = DataSupport.findAll(Province::class.java)
        if (provinceList!!.size > 0) {
            dataList.clear()
            for (province in provinceList!!) {
                dataList.add(province.provinceName!!)
            }
            adapter!!.notifyDataSetChanged()
            listView!!.setSelection(0)
            currentLevel = LEVEL_PROVINCE
        } else {
            val address = "http://guolin.tech/api/china"
            queryFromServer(address, "province")
        }
    }

    private fun queryCities() {
        titleText!!.text = selectedProvince!!.provinceName
        backButton!!.visibility = View.VISIBLE
        cityList = DataSupport.where("provinceId = ?", selectedProvince!!.provinceCode.toString()).find(City::class.java)
//        LogUtil.e("provinceId",selectedProvince!!.provinceCode.toString())
        if (cityList!!.size > 0) {
            dataList.clear()
            for (city in cityList!!) {
                dataList.add(city.cityName!!)
            }
            adapter!!.notifyDataSetChanged()
            listView!!.setSelection(0)
            currentLevel = LEVEL_CITY
        } else {
            val provinceCode = selectedProvince!!.provinceCode
            val address = "http://guolin.tech/api/china/$provinceCode"
            queryFromServer(address, "city")
        }
    }

    private fun queryCounties() {
        titleText!!.text = selectedCity!!.cityName
        backButton!!.visibility = View.VISIBLE
        countyList = DataSupport.where("cityId = ?", selectedCity!!.cityCode.toString()).find(County::class.java)
        if (countyList!!.size > 0) {
            dataList.clear()
            for (county in countyList!!) {
                dataList.add(county.countyName!!)
            }
            adapter!!.notifyDataSetChanged()
            listView!!.setSelection(0)
            currentLevel = LEVEL_COUNTY
        } else {
            val provinceCode = selectedProvince!!.provinceCode
            val cityCode = selectedCity!!.cityCode
            val address = "http://guolin.tech/api/china/$provinceCode/$cityCode"
            queryFromServer(address, "county")
        }
    }

    private fun queryFromServer(address: String, type: String) {
        showProgressDialog()
        HttpUtil.sendOkHttpRequest(address, object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity.runOnUiThread {
                    closeProgressDialog()
                    Toast.makeText(context, "加载失败,请检查网络连接!", Toast.LENGTH_SHORT).show()
                }
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val responseText = response.body().string()
//                LogUtil.e("reponse", responseText)
                var result = false
                if ("province" == type) {
                    result = Utility.handleProvinceRespose(responseText)
                } else if ("city" == type) {
                    result = Utility.handleCityResponse(responseText, selectedProvince!!.provinceCode)
                } else if ("county" == type) {
                    result = Utility.handleCountResponse(responseText, selectedCity!!.cityCode)
                }

                if (result) {
                    activity.runOnUiThread {
                        closeProgressDialog()
                        if ("province" == type) {
                            queryProvinces()
                        } else if ("city" == type) {
                            queryCities()
                        } else if ("county" == type) {
                            queryCounties()
                        }
                    }
                }else {
                    activity.runOnUiThread(Runnable {
                        closeProgressDialog()
                        Toast.makeText(activity,"获取信息失败",Toast.LENGTH_SHORT).show()
                    })
                }
            }
        })
    }

    private fun showProgressDialog() {
        if (progressDialog == null) {
            progressDialog = ProgressDialog(activity)
            progressDialog!!.setMessage("正在加载...")
            progressDialog!!.setCanceledOnTouchOutside(false)
        }
        progressDialog!!.show()
    }

    private fun closeProgressDialog() {
        if (progressDialog != null) {
            progressDialog!!.dismiss()
        }
    }

    companion object {

        val LEVEL_PROVINCE = 0
        val LEVEL_CITY = 1
        val LEVEL_COUNTY = 2
    }
}
