package org.arboristasurbanos.treeplant.ui.myforest

import android.app.*
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import org.arboristasurbanos.treeplant.BuildConfig.DEBUG
import org.arboristasurbanos.treeplant.R
import org.arboristasurbanos.treeplant.database.DatabaseHandler
import org.arboristasurbanos.treeplant.databinding.ActivityMapsBinding
import org.arboristasurbanos.treeplant.helper.locationServiceHelper
import org.arboristasurbanos.treeplant.model.TreeModelClass
import org.arboristasurbanos.treeplant.ui.planting.PlantingFragment
import java.util.*


class MyForestActivity : Fragment(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener, GoogleMap.OnMarkerDragListener {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var locationServiceHelper: locationServiceHelper
    private var clickCount = 0
    private var lastClickedId = -1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        val root: View = binding.root

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = childFragmentManager
            .findFragmentById(R.id.maps) as SupportMapFragment
        mapFragment.getMapAsync(this)

        return root
    }

    override fun onResume() {
        super.onResume()
        locationServiceHelper.startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        locationServiceHelper.stopLocationUpdates()
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        val databaseHandler: DatabaseHandler = DatabaseHandler(requireContext())
        val trees: List<TreeModelClass> = databaseHandler.viewTrees()
        mMap = googleMap

        for (tree in trees) {
            // Add a marker to tree and move the camera
            val treeLocation = LatLng(tree.getLat(), tree.getLong())
            val treeMarker = mMap.addMarker(
                MarkerOptions()
                    .position(treeLocation)
                    .title(tree.Name + " : " + tree.Date)
                    .snippet(getString(R.string.click_marker_options))
                   .draggable(true)
            )
            treeMarker?.tag = tree.Id
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(treeLocation, 15.0f))
        }
        locationServiceHelper = locationServiceHelper(requireContext(), requireActivity())
        locationServiceHelper.internalLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                val circleOptions = CircleOptions()
                    .center(
                        LatLng(
                            locationServiceHelper.lat,
                            locationServiceHelper.long
                        )
                    )
                    .radius(20.0) // radius in meters
                    .fillColor(0x8800CCFF.toInt()) //this is a half transparent blue, change "88" for the transparency
                    .strokeColor(Color.BLUE) //The stroke (border) is blue
                    .strokeWidth(10F) // The width is in pixel, so try it!

                // Get back the mutable Circle
                mMap.addCircle(circleOptions)
            }
        }
        locationServiceHelper.checkLocation()


        mMap.setOnMarkerClickListener(this)
        mMap.setOnMarkerDragListener(this)
    }
    /** Called when the user clicks a marker.  */
    override fun onMarkerClick(marker: Marker): Boolean {

        // Retrieve the data from the marker.
        val markerId = marker.tag as? Int
        clickCount++
        markerId?.let {
            if (lastClickedId == markerId) {
                val options = arrayOf(getString(R.string.action_edit), getString(R.string.action_delete), getString(R.string.action_navigate),getString(R.string.action_alarm))
                val builder: AlertDialog.Builder = AlertDialog.Builder(this.requireContext())
                builder.setTitle(getString(R.string.confirm_tree_options))
                    .setItems(options, DialogInterface.OnClickListener { dialog, which ->
                        when (which) {
                            0 -> { //Edit
                                var manager = this.activity?.getSupportFragmentManager()
                                var transaction = manager?.beginTransaction()
                                if (transaction != null) {
                                    val bundle = Bundle()
                                    var mFrag = PlantingFragment()
                                    bundle.putInt(
                                        "Id",
                                        markerId
                                    )
                                    mFrag.setArguments(bundle)
                                    transaction.replace(R.id.nav_host_fragment_content_main, mFrag)
                                    transaction.commit()
                                }
                            }
                            1 -> { //Delete
                                val confirmBuilder: AlertDialog.Builder =
                                    AlertDialog.Builder(this.requireContext())
                                confirmBuilder.setMessage(getString(R.string.confirm_tree_delete))
                                    .setNegativeButton(
                                        getString(R.string.no),
                                        DialogInterface.OnClickListener { dialog, id ->
                                        })
                                    .setPositiveButton(getString(R.string.yes),
                                        DialogInterface.OnClickListener { dialog, id ->
                                            val databaseHandler: DatabaseHandler =
                                                DatabaseHandler(this.requireContext())
                                            val dbReturn = databaseHandler.deleteTree(markerId)
                                            if (dbReturn) {
                                                startActivity(Intent.makeRestartActivityTask(this.requireActivity().intent?.component))
                                                if (DEBUG) {
                                                    Toast.makeText(
                                                        this.requireContext(),
                                                        "Deleted ${marker.title}",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        })
                                confirmBuilder.show()
                            }
                            2 -> { //Navigate
                                val lat = marker.position.latitude
                                val lon = marker.position.longitude
                                val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                intent.setPackage("com.google.android.apps.maps");
                                requireContext().startActivity(intent)
                            }
                            3 -> { //Schedule
                                val alert: AlertDialog.Builder = AlertDialog.Builder(context)
                                alert.setTitle(getString(R.string.prompt_title))
                                alert.setMessage(getString(R.string.prompt_days_quantity))
                                val input = EditText(context)
                                input.inputType = InputType.TYPE_CLASS_NUMBER
                                input.hint = "10"
                                input.setRawInputType(Configuration.KEYBOARD_12KEY)
                                alert.setView(input)
                                alert.setPositiveButton(
                                    "Ok"
                                ) { dialog, whichButton ->
                                    var days = Integer.parseInt(input.text.toString()) * 86400000
                                    var timeInMillis = (System.currentTimeMillis() + days)
                                    var title = marker.title + " " + requireContext().getString(R.string.notif1)
                                    var msg = requireContext().getString(R.string.notif2)
                                    val intent = Intent(Intent.ACTION_INSERT)
                                        .setData(CalendarContract.Events.CONTENT_URI)
                                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, timeInMillis)
                                        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, timeInMillis+(300000))
                                        .putExtra(CalendarContract.Events.TITLE, title)
                                        .putExtra(CalendarContract.Events.DESCRIPTION, msg)
                                        .putExtra(CalendarContract.Events.EVENT_LOCATION, marker.position.latitude.toString() + ", " + marker.position.longitude.toString())
                                        .putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
                                    startActivity(intent)

                                    /*
                                    var alarmMgr = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
                                    var alarmIntent = Intent(context, AlarmReceiver::class.java).let { intent ->
                                        intent.putExtra("Id",markerId)
                                        PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
                                    }
                                    var days = Integer.parseInt(input.text.toString()) * 86400000
                                    val calendar: Calendar = Calendar.getInstance().apply {
                                        timeInMillis = (System.currentTimeMillis() + days)
                                    }
                                    alarmMgr.setExact(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), alarmIntent)
                                    */
                                    Toast.makeText(
                                        this.requireContext(),
                                        "Alarm created with success",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                alert.setNegativeButton(getString(R.string.cancel),
                                    DialogInterface.OnClickListener { dialog, whichButton ->
                                    })
                                alert.show()
                            }
                        }
                    })
                builder.show()
        }
        else
        {
            clickCount = 0
        }
        this.lastClickedId=markerId
        }
        return false
    }


    override fun onMarkerDrag(marker: Marker) {
    }

    override fun onMarkerDragEnd(marker: Marker) {
        // Retrieve the data from the marker.
        val markerId = marker.tag as? Int
        markerId?.let {
            val builder: AlertDialog.Builder = AlertDialog.Builder(this.requireContext())
            builder.setMessage(getString(R.string.confirm_tree_update))
                .setNegativeButton(
                    getString(R.string.no),
                    DialogInterface.OnClickListener { dialog, id ->
                        val tree = getTree(markerId)
                        if (tree != null) {
                            marker.position = LatLng(tree.getLat(), tree.getLong())
                        }
                    })
                .setPositiveButton(getString(R.string.yes),
                    DialogInterface.OnClickListener { dialog, id ->
                        val databaseHandler: DatabaseHandler =
                            DatabaseHandler(this.requireContext())
                        val dbReturn = databaseHandler.updateTreeLocation(markerId, marker.position.latitude, marker.position.longitude)
                        if (dbReturn > 0) {
                            if (DEBUG) {
                                Toast.makeText(
                                    this.requireContext(),
                                    "Updated ${marker.title}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        else{
                            val tree = getTree(markerId)
                            if (tree != null) {
                                marker.position = LatLng(tree.getLat(), tree.getLong())
                            }
                        }
                    })
            builder.show()
        }
    }

    private fun getTree(markerId: Int): TreeModelClass? {
        val databaseHandler: DatabaseHandler = DatabaseHandler(requireContext())
        return databaseHandler.viewTree(markerId)
    }

    override fun onMarkerDragStart(marker: Marker) {
    }

}