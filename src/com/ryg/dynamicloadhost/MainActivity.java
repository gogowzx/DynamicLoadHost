package com.ryg.dynamicloadhost;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;

import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.ryg.utils.DLUtils;

public class MainActivity extends Activity implements OnItemClickListener {

    public static final String FROM = "extra.from";
    public static final int FROM_INTERNAL = 0;
    public static final int FROM_EXTERNAL = 1;

    private ArrayList<PluginItem> mPluginItems = new ArrayList<PluginItem>();
    private PluginAdapter mPluginAdapter;

    private ListView mListView;
    private Button mOpenClient;

    //android获取一个用于打开Word文件的intent

    

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initView();
        initData();
    }

    private void initView() {
        mPluginAdapter = new PluginAdapter();
        mListView = (ListView) findViewById(R.id.plugin_list);
        mListView.setAdapter(mPluginAdapter);
        mListView.setOnItemClickListener(this);
    }

    private void initData() {
        String pluginFolder = "/mnt/sdcard/Denesoft/YunSchool/download"; ///*"/mnt/sdcard/DynamicLoadHost"*/;
        File file = new File(pluginFolder);
        File[] plugins = file.listFiles(new FilenameFilter() {
			
			@Override
			public boolean accept(File dir, String filename) {
				return filename.endsWith(".apk");
				//return false;
			}
		});

        for (File plugin : plugins) {
            PluginItem item = new PluginItem();
            item.pluginPath = plugin.getAbsolutePath();
            item.packageInfo = DLUtils.getPackageInfo(this, item.pluginPath);
            mPluginItems.add(item);
        }

        mPluginAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    private class PluginAdapter extends BaseAdapter {

        private LayoutInflater mInflater;

        public PluginAdapter() {
            mInflater = MainActivity.this.getLayoutInflater();
        }

        @Override
        public int getCount() {
            return mPluginItems.size();
        }

        @Override
        public Object getItem(int position) {
            return mPluginItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.plugin_item, parent, false);
                holder = new ViewHolder();
                holder.appIcon = (ImageView) convertView.findViewById(R.id.app_icon);
                holder.appName = (TextView) convertView.findViewById(R.id.app_name);
                holder.apkName = (TextView) convertView.findViewById(R.id.apk_name);
                holder.packageName = (TextView) convertView.findViewById(R.id.package_name);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            PluginItem item = mPluginItems.get(position);
            PackageInfo packageInfo = item.packageInfo;
            holder.appIcon.setImageDrawable(DLUtils.getAppIcon(MainActivity.this, item.pluginPath));
            holder.appName.setText(DLUtils.getAppLabel(MainActivity.this, item.pluginPath));
            holder.apkName.setText(item.pluginPath.substring(item.pluginPath.lastIndexOf(File.separatorChar) + 1));
            holder.packageName.setText(packageInfo.applicationInfo.packageName);
            return convertView;
        }
    }

    private static class ViewHolder {
        public ImageView appIcon;
        public TextView appName;
        public TextView apkName;
        public TextView packageName;
    }

    public static class PluginItem {
        public PackageInfo packageInfo;
        public String pluginPath;

        public PluginItem() {
        }
    }

//    public static Intent getWordFileIntent(String param) {
//      Intent intent = new Intent("android.intent.action.VIEW");
//
//      intent.addCategory("android.intent.category.DEFAULT");
//
//      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//
//      Uri uri = Uri.fromFile(new File(param ));
//      intent.setDataAndType(uri, "application/msword");
//
//      return intent;
//
//    }
    
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
//    	Intent intent = getWordFileIntent("/mnt/sdcard/Denesoft/YunSchool/download/自助游系列App设想.doc");
//    	startActivity(intent);
        Intent intent = new Intent(this, ProxyActivity.class);
        intent.putExtra(ProxyActivity.EXTRA_DEX_PATH, mPluginItems.get(position).pluginPath);
        startActivity(intent);
    }

}
