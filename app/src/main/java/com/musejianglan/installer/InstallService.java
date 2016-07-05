package com.musejianglan.installer;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.musejianglan.installer.auto.PackageUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author 刘磊
 * 开启app自动更新服务，将apk下载至sdcard/egintra/文件夹中，删除旧apk文件，执行命令行安装app
 *
 */
public class InstallService extends IntentService {

	private static String name = "com.egintra.wfcx.appUpdateService";
	private static String TAG = "mService";

	private String filename = null;
	private String path = Environment.getExternalStorageDirectory().getPath() + "/egintra/";// 下载目录
	private String url = "http://124.128.251.130:8687/zzjf-webapp/Install.apk";// TODO 下载地址

	public InstallService() {
		super(name);
	}

	@Override
	protected void onHandleIntent(Intent intent) {

		Log.e(TAG, "update start");
		downFile(); // 下载apk文件
		Log.e(TAG, "update end");

	}

	/**
	 * 下载apk文件
	 */
	void downFile() {
		Log.e(TAG, "start download");
		URL myURL = null;
		InputStream is = null;
		FileOutputStream fos = null;
		try {

			filename = url.substring(url.lastIndexOf("/") + 1);

			myURL = new URL(url);
			HttpURLConnection conn = (HttpURLConnection) myURL.openConnection();
			conn.setRequestMethod("GET");
			conn.setReadTimeout(5 * 1000); // 设置过期时间为5秒
			conn.connect();
			is = conn.getInputStream();

			if (is == null) {
				throw new RuntimeException("stream is null");
			}

			File file1 = new File(path);//在根目录中新建egintra文件夹
			if (!file1.exists()) {
				file1.mkdirs();
			}
			File file2 = new File(path + filename);
			if (!file2.exists()) {
				file2.createNewFile();
			}

			fos = new FileOutputStream(path + filename);
			byte buf[] = new byte[1024];
			do {
				// 循环读取
				int numread = is.read(buf);
				if (numread == -1) {
					break;
				}
				fos.write(buf, 0, numread);

			} while (true);


			downComplete();

		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (fos !=null) {
					fos.close();
				}
				if (is!=null) {
					is.close();
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}

	}

	/**
	 * 下载完成，删除旧apk文件，执行安装
	 */
	void downComplete() {

		File file = new File(path);

		File[] files = file.listFiles();
		for (int i = 0; i < files.length; i++) {
			File file2 = files[i];
			Log.e(TAG, "file2.getName():"+file2.getName()+",filename:"+filename);
			if (!filename.contains(file2.getName())) {
				file2.delete();
			}
		}
		final int result = PackageUtils.installSilent(getApplicationContext(), path + filename);

		if (result == 1) {
			Log.e(TAG, "成功");
		}else {
			Log.e(TAG, "静默安装失败--:" + result+",执行标准安装");
//
//			boolean result2 = installNormal(getApplicationContext(), path + filename);
//			if (result2) {
//				Log.e(TAG, "成功");
//			}else {
//				Log.e(TAG, "失败---------------");
//			}

			String result2 = installSilently();
			Log.e(TAG, result2);
		}

	}

	/**
	 * 调用官方安装器进行安装
	 * @param context
	 * @param filePath
	 * @return
	 */
	public static boolean installNormal(Context context, String filePath) {
		Intent i = new Intent(Intent.ACTION_VIEW);
		File file = new File(filePath);
		if (file == null || !file.exists() || !file.isFile() || file.length() <= 0) {
			return false;
		}

		i.setDataAndType(Uri.parse("file://" + filePath), "application/vnd.android.package-archive");
		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		context.startActivity(i);
		return true;
	}

	/**
	 * 执行"pm inistall -r apkPath"安装apk
	 *
	 * @return
	 */
	private String installSilently() {
		Log.e(TAG, "start install");
		String apkPath = path + filename;
		File file = new File(apkPath);
		if (!file.exists()) {
			return null;
		}
		// 通过命令行来安装APK
//		String[] args = { "pm", "install", apkPath };
		String[] args = { "pm", "install", "-r", apkPath };
//		-r: 安装一个已经安装的APK，保持其数据不变。
//		-i：指定安装的包名。(没试出来)
//		-s: 安装到SDCard上。
//		-f: 安装到内部Flash上。
		String result = "";
		// 创建一个操作系统进程并执行命令行操作
		ProcessBuilder processBuilder = new ProcessBuilder(args);
		Process process = null;
		InputStream errIs = null;
		InputStream inIs = null;
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			int read = -1;
			process = processBuilder.start();
			errIs = process.getErrorStream();
			while ((read = errIs.read()) != -1) {
				baos.write(read);
			}
			baos.write('\n');
			inIs = process.getInputStream();
			while ((read = inIs.read()) != -1) {
				baos.write(read);
			}
			byte[] data = baos.toByteArray();
			result = new String(data);
		} catch (IOException e) {
			Log.e(TAG, "install exception");
			e.printStackTrace();
		} catch (Exception e) {
			Log.e(TAG, "install exception2");
			e.printStackTrace();
		} finally {
			try {
				if (errIs != null) {
					errIs.close();
				}
				if (inIs != null) {
					inIs.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (process != null) {
				process.destroy();
			}
		}
		return result;
	}



	/**
	 * 获取当前app的版本号
	 *
	 * @param context
	 * @return
	 */
	public static int getVerCode(Context context) {
		int verCode = -1;
		try {
			verCode = context.getPackageManager().getPackageInfo("com.egintra.wfcx", 0).versionCode;
		} catch (NameNotFoundException e) {
			Log.e(TAG, e.getMessage());
		}
		return verCode;
	}

	/**
	 * 获取当前app的版本名称
	 *
	 * @param context
	 * @return
	 */
	public static String getVerName(Context context) {
		String verName = "";
		try {
			verName = context.getPackageManager().getPackageInfo("com.egintra.wfcx", 0).versionName;
		} catch (NameNotFoundException e) {
			Log.e(TAG, e.getMessage());
		}
		return verName;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		Log.e(TAG, "service destroy");
	}

}
