package get.mobile.mifare.demo

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcA
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager.widget.ViewPager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.jraska.console.Console
import get.mobile.mifare.demo.ui.main.SectionsPagerAdapter
import timber.log.Timber
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private fun onTagDiscovered(tag: Tag){
        Console.clear()
        Timber.d("onTagDiscovered")

        //see for all ulc commands!
        //https://www.nxp.com/docs/en/data-sheet/MF0ICU2.pdf


        val nfc: NfcA = NfcA.get(tag)

        try{
            nfc.connect()
        }catch (e: Exception){
            Timber.e("error connecting chip: %s", e.message)
        }

        try{
           Timber.d("tagId: %s", tag.id.toHexString())
            Timber.d("atqa: %s", nfc.atqa.toHexString())
            Timber.d("sak: %s", nfc.sak)
        }catch (e: Exception){
            Timber.e("error reading chip header: %s", e.message)
        }

        readTag(nfc)

        writeTagRandomData(nfc,8)
        writeTagRandomData(nfc,20)
        writeTagRandomData(nfc,32)

        readTag(nfc)

        try {
            nfc.close()
        }catch (e: Exception){
            Timber.e("error closing chip: %s", e.message)
        }
    }

    private fun readTag(nfc: NfcA){
        try {
            Timber.d("dump full chip")
            //ULC pages from 0 to 47
            //we are unable to read all pages this is ok as some pages are locked
            //just for demo
            for (i in 0..47 step 4) {
                //0x30 -> read 4 pages starting at i
                val cmd = byteArrayOf(0x30, i.toByte())
                val readPages = nfc.transceive(cmd)
                Timber.d("page ($i - ${i + 3}) content: %s", readPages.toHexString())
            }
        }catch (e: Exception){
            Timber.e("error reading pages: %s", e.message)
        }
    }

    private fun writeTagRandomData(nfc: NfcA, pageNr: Byte){
        try {
            val randomBytes = Random.nextBytes(4)
            Timber.d("writing to pageNr: %s data: %s",pageNr,randomBytes.toHexString())

            //command, pageNr, data[0-3] (4 Bytes data)
            //0xA2 -> write 1 page (4 Bytes)
            val cmd = byteArrayOf(0xA2.toByte(), pageNr) + randomBytes
            nfc.transceive(cmd)

        }catch (e: Exception){
            Timber.e("error writing pages: %s", e.message)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val sectionsPagerAdapter = SectionsPagerAdapter(this, supportFragmentManager)
        val viewPager: ViewPager = findViewById(R.id.view_pager)
        viewPager.adapter = sectionsPagerAdapter
        val tabs: TabLayout = findViewById(R.id.tabs)
        tabs.setupWithViewPager(viewPager)
        val fab: FloatingActionButton = findViewById(R.id.fab)

        fab.visibility = View.GONE

        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }
    }

    // this is the foreground dispatch callback
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Timber.d("NfcBase calling onNewIntent")
        if (intent != null) {
            Timber.d("intent: %s", intent.toString())
        }
        handleNewNfcIntent(intent)
    }

    private fun handleNewNfcIntent(intent: Intent?) {
        Timber.d("NfcBase calling handleNewNfcIntent")
        if (!(NfcAdapter.ACTION_TAG_DISCOVERED == intent!!.action || NfcAdapter.ACTION_TECH_DISCOVERED == intent.action || NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action)) {
            Timber.d("NfcBase unknown intent returning")
            return
        }

        Timber.d("we found an tag!")
        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        if(tag != null){
            onTagDiscovered(tag)
        }
    }


    private fun getNfcAdapter(): NfcAdapter{
        return NfcAdapter.getDefaultAdapter(this)
    }

    override fun onResume() {
        super.onResume()
        Timber.d("NfcBase calling onResume")
        setupForegroundDispatch(this)
    }

    override fun onPause() {
        Timber.d("NfcBase calling onPause")
        stopForegroundDispatch(this)
        super.onPause()
    }

    /**
     * @param activity The corresponding [Activity] requesting the foreground dispatch.
     * @param adapter  The [NfcAdapter] used for the foreground dispatch.
     */
    fun setupForegroundDispatch(activity: Activity) {
        Timber.v("NfcBase -> enableForegroundDispatch start")

        while (!getNfcAdapter().isEnabled) {
            Timber.e("NfcBase -> setupForegroundDispatch -> nfc not enabled")
            try {
                Thread.sleep(100)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
        val intent = Intent(activity.applicationContext, activity.javaClass)
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        val pendingIntent = PendingIntent.getActivity(activity.applicationContext, 0, intent, 0)
        val filters = arrayOfNulls<IntentFilter>(1)
        val techList = arrayOf<Array<String>>()

        // Notice that this is the same filter as in our manifest.
        filters[0] = IntentFilter()
        filters[0]!!.addAction(NfcAdapter.ACTION_NDEF_DISCOVERED)
        filters[0]!!.addAction(NfcAdapter.ACTION_TAG_DISCOVERED)
        filters[0]!!.addAction(NfcAdapter.ACTION_TECH_DISCOVERED)
        filters[0]!!.addCategory(Intent.CATEGORY_DEFAULT)
        //try {
        //	filters[0].addDataType(MIME_TEXT_PLAIN);
        //} catch (IntentFilter.MalformedMimeTypeException e) {
        //	throw new RuntimeException("Check your mime type.");
        //}
        getNfcAdapter().enableForegroundDispatch(activity, pendingIntent, filters, techList)
        Timber.v("NfcBase -> enableForegroundDispatch done")
    }

    /**
     * @param activity The corresponding [BaseActivity] requesting to stop the foreground dispatch.
     * @param adapter  The [NfcAdapter] used for the foreground dispatch.
     */
    fun stopForegroundDispatch(activity: Activity) {
        Timber.v("NfcBase -> stopForegroundDispatch start")
        getNfcAdapter().disableForegroundDispatch(activity)
        Timber.v("NfcBase -> stopForegroundDispatch done")
    }

    fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
}