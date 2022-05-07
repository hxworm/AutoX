package org.autojs.autojs.ui.shortcut

import org.androidannotations.annotations.EActivity
import org.autojs.autojs.ui.BaseActivity
import org.androidannotations.annotations.ViewById
import androidx.recyclerview.widget.RecyclerView
import android.content.pm.PackageManager
import org.androidannotations.annotations.AfterViews
import org.autojs.autojs.workground.WrapContentGridLayoutManger
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.graphics.Bitmap
import org.autojs.autojs.tool.BitmapTool
import android.graphics.BitmapFactory
import android.view.*
import android.widget.ImageView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.R
import java.lang.IllegalArgumentException
import java.util.ArrayList

/**
 * Created by Stardust on 2017/10/25.
 */
@EActivity(R.layout.activity_shortcut_icon_select)
open class ShortcutIconSelectActivity : BaseActivity() {
    @JvmField
    @ViewById(R.id.apps)
    var mApps: RecyclerView? = null
    private var mPackageManager: PackageManager? = null
    private val mAppList: MutableList<AppItem> = ArrayList()

    @AfterViews
    fun setupViews() {
        mPackageManager = packageManager
        setToolbarAsBack(getString(R.string.text_select_icon))
        setupApps()
    }

    private fun setupApps() {
        mApps!!.adapter = AppsAdapter()
        val manager = WrapContentGridLayoutManger(this, 5)
        manager.setDebugInfo("IconSelectView")
        mApps!!.layoutManager = manager
        loadApps()
    }

    @SuppressLint("CheckResult")
    private fun loadApps() {
        val packages = mPackageManager!!.getInstalledApplications(PackageManager.GET_META_DATA)
        Observable.fromIterable(packages)
            .observeOn(Schedulers.computation())
            .filter { appInfo: ApplicationInfo -> appInfo.icon != 0 }
            .map { info: ApplicationInfo -> AppItem(info) }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { icon: AppItem ->
                mAppList.add(icon)
                mApps!!.adapter!!.notifyItemInserted(mAppList.size - 1)
            }
    }

    private fun selectApp(appItem: AppItem) {
        setResult(
            RESULT_OK, Intent()
                .putExtra(EXTRA_PACKAGE_NAME, appItem.info.packageName)
        )
        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_shortcut_icon_select, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        startActivityForResult(
            Intent(Intent.ACTION_GET_CONTENT)
                .setType("image/*"), 11234
        )
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            setResult(RESULT_OK, data)
            finish()
        }
    }

    private inner class AppItem(var info: ApplicationInfo) {
        var icon: Drawable

        init {
            icon = info.loadIcon(mPackageManager)
        }
    }

    private inner class AppIconViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var icon: ImageView

        init {
            icon = itemView as ImageView
            icon.setOnClickListener { v: View? -> selectApp(mAppList[adapterPosition]) }
        }
    }

    private inner class AppsAdapter : RecyclerView.Adapter<AppIconViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppIconViewHolder {
            return AppIconViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.app_icon_list_item, parent, false)
            )
        }

        override fun onBindViewHolder(holder: AppIconViewHolder, position: Int) {
            holder.icon.setImageDrawable(mAppList[position].icon)
        }

        override fun getItemCount(): Int {
            return mAppList.size
        }
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"

        @JvmStatic
        fun getBitmapFromIntent(context: Context, data: Intent): Observable<Bitmap> {
            val packageName = data.getStringExtra(EXTRA_PACKAGE_NAME)
            if (packageName != null) {
                return Observable.fromCallable {
                    val drawable = context.packageManager.getApplicationIcon(packageName)
                    BitmapTool.drawableToBitmap(drawable)
                }
            }
            val uri = data.data
                ?: return Observable.error(IllegalArgumentException("invalid intent"))
            return Observable.fromCallable {
                BitmapFactory.decodeStream(
                    context.contentResolver.openInputStream(
                        uri
                    )
                )
            }
        }

        suspend fun getBitmapFromIntent1(context: Context, data: Intent): Bitmap? {
            val bitmap = withContext(Dispatchers.IO) {
                data.getStringExtra(EXTRA_PACKAGE_NAME)?.let {
                    val drawable = context.packageManager.getApplicationIcon(it)
                    return@withContext BitmapTool.drawableToBitmap(drawable)
                }
                data.data?.let {
                    return@withContext BitmapFactory.decodeStream(
                        context.contentResolver.openInputStream(it)
                    )
                }
                return@withContext null
            }
            return withContext(Dispatchers.Main) {
                bitmap
            }
        }

    }
}