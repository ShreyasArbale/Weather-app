package com.example.weatherapp

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.location.LocationRequest
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.example.weatherapp.models.WeatherResponse
import com.example.weatherapp.network.WeatherService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone


class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationProviderClient: FusedLocationProviderClient

    private var mProgressDialog: Dialog? =null

    private lateinit var mSharedPreferences: SharedPreferences

    private var tv_main: TextView? = null
    private var tv_main_description: TextView? = null
    private var tv_temp: TextView? = null
    private var tv_sunrise_time: TextView? = null
    private var tv_sunset_time: TextView? = null
    private var tv_humidity: TextView? = null
    private var tv_pressure: TextView? = null
    private var tv_min: TextView? = null
    private var tv_max: TextView? = null
    private var tv_speed: TextView? = null
    private var tv_name: TextView? = null
    private var tv_country: TextView? = null
    private var iv_main: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tv_main = findViewById(R.id.tv_main)
        tv_main_description = findViewById(R.id.tv_main_description)
        tv_temp = findViewById(R.id.tv_temp)
        tv_sunrise_time = findViewById(R.id.tv_sunrise_time)
        tv_sunset_time = findViewById(R.id.tv_sunset_time)
        tv_humidity = findViewById(R.id.tv_humidity)
        tv_pressure = findViewById(R.id.tv_pressure)
        tv_min = findViewById(R.id.tv_min)
        tv_max = findViewById(R.id.tv_max)
        tv_speed = findViewById(R.id.tv_speed)
        tv_name = findViewById(R.id.tv_name)
        tv_country = findViewById(R.id.tv_country)
        iv_main = findViewById(R.id.iv_main)

        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

        setupUI()

        if(!isLocationEnable()){
            Toast.makeText(this@MainActivity, "Your location is off. Please turned it on.", Toast.LENGTH_SHORT).show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)

        }else{
                Dexter.withActivity(this)
                    .withPermissions(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                    .withListener(object : MultiplePermissionsListener{
                        override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                            if (report!!.areAllPermissionsGranted()){

                                requestLocationData()

                            }

                            if (report.isAnyPermissionPermanentlyDenied){
                                Toast.makeText(this@MainActivity,"You have denied location permissions, kindly turned it on.",Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onPermissionRationaleShouldBeShown(
                            permissions: MutableList<PermissionRequest>?,
                            token: PermissionToken?
                        ) {
                            showRationalDialogForPermissions()
                        }
                    }).onSameThread()
                    .check()
        }

    }


    @SuppressLint("MissingPermission")
    private fun requestLocationData(){

        // Change instate of 'LocationRequest()' , I use com.google.android.gms.location.LocationRequest()

        val mLocationRequest = com.google.android.gms.location.LocationRequest()
        mLocationRequest.priority = com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationProviderClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation : Location = locationResult.lastLocation
            val latitude = mLastLocation.latitude
            Log.i("Current Latitude","$latitude")

            val longitude = mLastLocation.longitude
            Log.i("Current Longitude","$longitude")
            getLocationWeatherDetails(latitude, longitude)
        }
    }

    private fun getLocationWeatherDetails(latitude: Double, longitude:Double){
        if (Constants.isNetworkAvailable(this)){

            val retrofit : Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service: WeatherService = retrofit
                .create<WeatherService>(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID
            )
            showCustomProgressDialog()

            listCall.enqueue(object : Callback<WeatherResponse>{
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if (response.isSuccessful){

                        hideProgressDialog()

                        val weatherList: WeatherResponse? = response.body()

                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()

                        setupUI()

                        Log.i("Response Result", "$weatherList")
                    }else{
                        val rc = response.code()
                        when(rc){
                            400 -> {
                                Log.e("Error 400" , "Bad Connection")
                            }
                            404 -> {
                                Log.e("Error 404","Not Found")
                            }else -> {
                                Log.e("Error","Generic Error")
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e("Error",t.message.toString())
                    hideProgressDialog()
                }

            })


        }else{
            Toast.makeText(this@MainActivity,"No internet connection is available.",Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRationalDialogForPermissions(){
        AlertDialog.Builder(this)
            .setMessage("It looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
            .setPositiveButton(
                "GO TO SETTINGS"
            ){ _,_ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName,null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException){
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel"){dialog,
                                        _ ->
                dialog.dismiss()
            }.show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.action_refresh ->{
                requestLocationData()
                true
            }else -> super.onOptionsItemSelected(item)
        }
    }

    private fun isLocationEnable(): Boolean{

        val locationManager: LocationManager=
            getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showCustomProgressDialog(){
        mProgressDialog = Dialog(this)
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()
    }

    private fun hideProgressDialog(){
        if (mProgressDialog != null){
            mProgressDialog!!.dismiss()
        }
    }

    private fun setupUI(){

        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA,"")

        if (!weatherResponseJsonString.isNullOrEmpty()){

            val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)
            for (i in weatherList.weather.indices){
                Log.i("Weather Name", weatherList.weather.toString())

                tv_main?.text = weatherList.weather[i].main
                tv_main_description?.text = weatherList.weather[i].description
                tv_temp?.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())

                tv_humidity?.text = weatherList.main.humidity.toString() + " %"
                tv_pressure?.text = weatherList.main.pressure.toString() + " hPa"
                tv_min?.text = weatherList.main.temp_min.toString() + getUnit(application.resources.configuration.locales.toString())+ " min"
                tv_max?.text = weatherList.main.temp_max.toString() + getUnit(application.resources.configuration.locales.toString())+ " max"
                tv_speed?.text = weatherList.wind.speed.toString()
                tv_name?.text = weatherList.name
                tv_country?.text = weatherList.sys.country

                tv_sunrise_time?.text = unixTime(weatherList.sys.sunrise) + " am"
                tv_sunset_time?.text = unixTime(weatherList.sys.sunset) + " pm"

                when(weatherList.weather[i].icon){
                    "01d" -> iv_main?.setImageResource(R.drawable.sunny)
                    "02d" -> iv_main?.setImageResource(R.drawable.cloud)
                    "03d" -> iv_main?.setImageResource(R.drawable.cloud)
                    "04d" -> iv_main?.setImageResource(R.drawable.cloud)
                    "04n" -> iv_main?.setImageResource(R.drawable.cloud)
                    "10d" -> iv_main?.setImageResource(R.drawable.rain)
                    "11d" -> iv_main?.setImageResource(R.drawable.storm)
                    "13d" -> iv_main?.setImageResource(R.drawable.snowflake)
                    "01n" -> iv_main?.setImageResource(R.drawable.cloud)
                    "02n" -> iv_main?.setImageResource(R.drawable.cloud)
                    "03n" -> iv_main?.setImageResource(R.drawable.cloud)
                    "10n" -> iv_main?.setImageResource(R.drawable.cloud)
                    "11n" -> iv_main?.setImageResource(R.drawable.rain)
                    "13n" -> iv_main?.setImageResource(R.drawable.snowflake)
                }
            }
        }


    }

    private fun getUnit(value: String):String?{
        var value = "°C"
        if ("US" == value || "LR" == value || "MM" == value){
            value = "°F"
        }
        return value
    }

    private fun unixTime(timex: Long): String?{
        val date = Date(timex *1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.UK)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

}