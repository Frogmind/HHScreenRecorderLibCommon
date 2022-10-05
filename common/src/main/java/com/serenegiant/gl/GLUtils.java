package com.serenegiant.gl;
/*
 * libcommon
 * utility/helper classes for myself
 *
 * Copyright (c) 2014-2022 saki t_saki@serenegiant.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
*/

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.Matrix;
import android.os.Build;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.serenegiant.system.Stacktrace;
import com.serenegiant.utils.AssetsHelper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;
import androidx.core.content.res.ResourcesCompat;

import static com.serenegiant.glutils.IMirror.*;
import static com.serenegiant.utils.BufferHelper.SIZEOF_FLOAT_BYTES;

/**
 * OpenGL|ES関係のヘルパーメソッド
 * XXX フレームワークのGLUtilsと被るのでリネームした方がいいかも？
 */
public class GLUtils implements GLConst {
	private static final boolean DEBUG = false;	// set false on production
	private static final String TAG = GLUtils.class.getSimpleName();

	private GLUtils() {
		// インスタンス化を防ぐためにデフォルトコンストラクタをprivateに
	}

	private static int sSupportedGLVersion = -1;

	/**
	 * 対応しているSurfaceかどうかを確認
	 * Surface/SurfaceHolder/SurfaceTexture/SurfaceViewならtrue
	 * @param surface
	 * @return
	 */
	public static boolean isSupportedSurface(@Nullable final Object surface) {
		return ((surface instanceof Surface)
			|| (surface instanceof SurfaceHolder)
			|| (surface instanceof SurfaceTexture)
			|| (surface instanceof SurfaceView));
	}

	/**
	 * 対応しているGL|ESのバージョンを取得
	 * XXX GLES30はAPI>=18以降なんだけどAPI=18でもGLコンテキスト生成に失敗する端末があるのでAP1>=21に変更
	 *     API>=21でGL_OES_EGL_image_external_essl3に対応していれば3, そうでなければ2を返す
	 * @return
	 */
	public static synchronized int getSupportedGLVersion() {
		if (sSupportedGLVersion < 1) {
			// 一度も実行されていない時
			final AtomicInteger result = new AtomicInteger(1);
			final Semaphore sync = new Semaphore(0);
			final GLContext context = new GLContext(3, null, 0);
			// ダミースレッド上でEGL/GLコンテキストを生成してエクステンション文字列をチェックする
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						context.initialize();
						String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS); // API >= 8
						if (DEBUG) Log.i(TAG, "getSupportedGLVersion:" + extensions);
						if ((extensions == null) || !extensions.contains("GL_OES_EGL_image_external")) {
							// GL_OES_EGL_image_externalが存在していない
							result.set(1);
						} else if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) && context.isGLES3()) {
							// API>=21でGLContextがGLES3で初期化できた時はGL_OES_EGL_image_external_essl3をチェックする
							extensions = GLES30.glGetString(GLES30.GL_EXTENSIONS); 	// API >= 18
							result.set((extensions != null) && extensions.contains("GL_OES_EGL_image_external_essl3")
								? 3 : 2);
						} else {
							result.set(2);
						}
					} catch (final Exception e) {
						Log.w(TAG, e);
					} finally {
						context.release();
						sync.release();
					}
				}
			}).start();
			try {
				if (sync.tryAcquire(500, TimeUnit.MILLISECONDS)) {
					sSupportedGLVersion = result.get();
				}
			} catch (final InterruptedException e) {
				// ignore
			}
		}
		if (DEBUG) Log.i(TAG, "getSupportedGLVersion:" + sSupportedGLVersion);
		return sSupportedGLVersion;
	}

	/**
	 * モデルビュー変換行列に左右・上下反転をセット
	 * @param mvp
	 * @param mirror
	 */
	public static void setMirror(@NonNull @Size(min=16) final float[] mvp, @MirrorMode final int mirror) {
		switch (mirror) {
		case MIRROR_NORMAL:
			mvp[0] = Math.abs(mvp[0]);
			mvp[5] = Math.abs(mvp[5]);
			break;
		case MIRROR_HORIZONTAL:
			mvp[0] = -Math.abs(mvp[0]);	// flip left-right
			mvp[5] = Math.abs(mvp[5]);
			break;
		case MIRROR_VERTICAL:
			mvp[0] = Math.abs(mvp[0]);
			mvp[5] = -Math.abs(mvp[5]);	// flip up-side down
			break;
		case MIRROR_BOTH:
			mvp[0] = -Math.abs(mvp[0]);	// flip left-right
			mvp[5] = -Math.abs(mvp[5]);	// flip up-side down
			break;
		}
	}

	/**
	 * 現在のモデルビュー変換行列をxy平面で指定した角度回転させる
	 * @param mvp
	 * @param degrees
	 */
	public static void rotate(@NonNull @Size(min=16) final float[] mvp, final int degrees) {
		if ((degrees % 180) != 0) {
			Matrix.rotateM(mvp, 0, degrees, 0.0f, 0.0f, 1.0f);
		}
	}

	/**
	 * モデルビュー変換行列にxy平面で指定した角度回転させた回転行列をセットする
	 * @param mvp
	 * @param degrees
	 */
	public static void setRotation(@NonNull @Size(min=16) final float[] mvp, final int degrees) {
		Matrix.setIdentityM(mvp, 0);
		if ((degrees % 180) != 0) {
			Matrix.rotateM(mvp, 0, degrees, 0.0f, 0.0f, 1.0f);
		}
	}

	public static int gLTextureUnit2Index(final int glTextureUnit) {
		return (glTextureUnit >= GLES20.GL_TEXTURE0) && (glTextureUnit <= GLES20.GL_TEXTURE31)
			? glTextureUnit - GLES20.GL_TEXTURE0 : 0;
	}

	/**
	 * GLES20.glReadPixelsのヘルパーメソッド
	 * RGBA8888として読み取る(=1ピクセル4バイト)
	 * orderをLITTLE_ENDIANにセットするのでBitmap#copyPixelsFromBufferへ直接引き渡すことができる
	 * @param buffer nullまたはサイズが小さいかまたはでないときは新規生成する
	 * @param width
	 * @param height
	 * @return 読み取ったピクセルデータの入ったByteBuffer, orderはLITTLE_ENDIAN
	 */
	@NonNull
	public static ByteBuffer glReadPixels(
		@Nullable final ByteBuffer buffer,
		@IntRange(from=1) final int width, @IntRange(from=1) final int height) {

		final int sz = width * height * 4;
		ByteBuffer buf = buffer;
		if ((buf == null) || (buf.capacity() < sz)) {
			if (DEBUG) Log.v(TAG, "glReadPixels:allocate direct bytebuffer");
			buf = ByteBuffer.allocateDirect(sz).order(ByteOrder.LITTLE_ENDIAN);
		}
		if ((buf.order() != ByteOrder.LITTLE_ENDIAN)) {
			buf.order(ByteOrder.LITTLE_ENDIAN);
		}
		buf.clear();
		// XXX GL|ES3の時はPBOとglMapBufferRange/glUnmapBufferを使うようにする?
		GLES20.glReadPixels(0, 0, width, height,
			GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);
		if (DEBUG) checkGlError("glReadPixels");
		buf.position(sz);
		buf.flip();

		return buf;
	}

	/**
	 * OpenGL|ESのエラーをチェックしてlogCatに出力する
	 * @param op
	 */
    public static void checkGlError(final String op) {
        final int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            final String msg = op + ": glError 0x" + Integer.toHexString(error);
			Log.e(TAG, msg);
			Stacktrace.print();
         	if (DEBUG) {
	            throw new RuntimeException(msg);
	       	}
        }
    }

	/**
	 * テクスチャ名を生成, クランプ方法はGL_CLAMP_TO_EDGE
	 * @param texTarget テクスチャのタイプ, GL_TEXTURE_EXTERNAL_OESかGL_TEXTURE_2D
	 * @param texUnit テクスチャユニット, GL_TEXTURE0...GL_TEXTURE31
	 * @param filterParam テクスチャの補完方法を指定, min/mag共に同じ値になる, GL_LINEARとかGL_NEAREST
	 * @return
	 */
	public static int initTex(
		@TexTarget final int texTarget, @TexUnit final int texUnit,
		@MinMagFilter final int filterParam) {

		return initTex(texTarget, texUnit,
			filterParam, filterParam, GLES20.GL_CLAMP_TO_EDGE);
	}

	/**
	 * テクスチャ名を生成(GL_TEXTURE0のみ)
	 * @param texTarget テクスチャのタイプ, GL_TEXTURE_EXTERNAL_OESかGL_TEXTURE_2D
	 * @param texUnit テクスチャユニット, GL_TEXTURE0...GL_TEXTURE31
	 * @param minFilter テクスチャの補間方法を指定, GL_LINEARとかGL_NEAREST
	 * @param magFilter テクスチャの補間方法を指定, GL_LINEARとかGL_NEAREST
	 * @param wrap テクスチャのクランプ方法, GL_CLAMP_TO_EDGE等
	 * @return
	 */
	public static int initTex(
		@TexTarget final int texTarget, @TexUnit final int texUnit,
		@MinMagFilter final int minFilter, @MinMagFilter final int magFilter,
		@Wrap final int wrap) {

		if (DEBUG) Log.v(TAG, "initTex:target=" + texTarget);
		final int[] tex = new int[1];
		GLES20.glActiveTexture(texUnit);
		GLES20.glGenTextures(1, tex, 0);
		GLES20.glBindTexture(texTarget, tex[0]);
		GLES20.glTexParameteri(texTarget, GLES20.GL_TEXTURE_WRAP_S, wrap);
		GLES20.glTexParameteri(texTarget, GLES20.GL_TEXTURE_WRAP_T, wrap);
		GLES20.glTexParameteri(texTarget, GLES20.GL_TEXTURE_MIN_FILTER, minFilter);
		GLES20.glTexParameteri(texTarget, GLES20.GL_TEXTURE_MAG_FILTER, magFilter);
		Log.d(TAG, "initTex:texId=" + tex[0]);
		return tex[0];
	}

	/**
	 * テクスチャ名配列を生成(前から順にGL_TEXTURE0, GL_TEXTURE1, ...), クランプ方法はGL_CLAMP_TO_EDGE
	 * @param n 生成するテキスチャ名の数, 最大で32個(GL_MAX_TEXTURE_IMAGE_UNITS以下)
	 * @param texTarget テクスチャのタイプ, GL_TEXTURE_EXTERNAL_OESかGL_TEXTURE_2D
	 * @param filterParam テクスチャの補完方法を指定, min/mag共に同じ値になる, GL_LINEARとかGL_NEAREST
	 * @return
	 */
	public static int[] initTexes(
		final int n,
		@TexTarget final int texTarget, @MinMagFilter final int filterParam) {

		return initTexes(new int[n], texTarget,
			filterParam, filterParam, GLES20.GL_CLAMP_TO_EDGE);
	}

	/**
	 * テクスチャ名配列を生成(前から順にGL_TEXTURE0, GL_TEXTURE1, ...), クランプ方法はGL_CLAMP_TO_EDGE
	 * @param texIds テクスチャ名配列, 最大で32個(GL_MAX_TEXTURE_IMAGE_UNITS以下)
	 * @param texTarget テクスチャのタイプ, GL_TEXTURE_EXTERNAL_OESかGL_TEXTURE_2D
	 * @param filterParam テクスチャの補完方法を指定, min/mag共に同じ値になる, GL_LINEARとかGL_NEAREST
	 * @return
	 */
	public static int[] initTexes(
		@NonNull final int[] texIds,
		@TexTarget final int texTarget, @MinMagFilter final int filterParam) {

		return initTexes(texIds, texTarget,
			filterParam, filterParam, GLES20.GL_CLAMP_TO_EDGE);
	}

	/**
	 * テクスチャ名配列を生成(前から順にGL_TEXTURE0, GL_TEXTURE1, ...)
 	 * @param n 生成するテキスチャ名の数, 最大32
	 * @param texTarget テクスチャのタイプ, GL_TEXTURE_EXTERNAL_OESかGL_TEXTURE_2D
	 * @param minFilter テクスチャの補間方法を指定, GL_LINEARとかGL_NEAREST
	 * @param magFilter テクスチャの補間方法を指定, GL_LINEARとかGL_NEAREST
	 * @param wrap テクスチャのクランプ方法, GL_CLAMP_TO_EDGE等
	 * @return
	 */
	public static int[] initTexes(
		final int n,
		@TexTarget final int texTarget,
		@MinMagFilter final int minFilter, @MinMagFilter final int magFilter,
		@Wrap final int wrap) {

		return initTexes(new int[n], texTarget, minFilter, magFilter, wrap);
	}

	/**
	 * テクスチャ名配列を生成(前から順にGL_TEXTURE0, GL_TEXTURE1, ...)
	 * @param texIds テクスチャ名配列, 最大で32個(GL_MAX_TEXTURE_IMAGE_UNITS以下)
	 * @param texTarget テクスチャのタイプ, GL_TEXTURE_EXTERNAL_OESかGL_TEXTURE_2D
	 * @param minFilter テクスチャの補間方法を指定, GL_LINEARとかGL_NEAREST
	 * @param magFilter テクスチャの補間方法を指定, GL_LINEARとかGL_NEAREST
	 * @param wrap テクスチャのクランプ方法, GL_CLAMP_TO_EDGE等
	 * @return
	 */
	public static int[] initTexes(
		@NonNull final int[] texIds,
		@TexTarget final int texTarget,
		@MinMagFilter final int minFilter, @MinMagFilter final int magFilter,
		@Wrap final int wrap) {

		int[] textureUnits = new int[1];
		GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_IMAGE_UNITS, textureUnits, 0);
		Log.v(TAG, "GL_MAX_TEXTURE_IMAGE_UNITS=" + textureUnits[0]);
		final int n = Math.min(texIds.length, textureUnits[0]);
		for (int i = 0; i < n; i++) {
			texIds[i] = initTex(texTarget, ShaderConst.TEX_NUMBERS[i],
				minFilter, magFilter, wrap);
		}
		return texIds;
	}

	/**
	 * テクスチャ名配列を生成(こっちは全部同じテクスチャユニット)
	 * @param n 最大で32個(GL_MAX_TEXTURE_IMAGE_UNITS以下)
	 * @param texTarget テクスチャのタイプ, GL_TEXTURE_EXTERNAL_OESかGL_TEXTURE_2D
	 * @param texUnit テクスチャユニット
	 * @param minFilter テクスチャの補間方法を指定, GL_LINEARとかGL_NEAREST
	 * @param magFilter テクスチャの補間方法を指定, GL_LINEARとかGL_NEAREST
	 * @param wrap テクスチャのクランプ方法, GL_CLAMP_TO_EDGE等
	 * @return
	 */
	public static int[] initTexes(
		final int n,
		@TexTarget final int texTarget, @TexUnit final int texUnit,
		@MinMagFilter final int minFilter, @MinMagFilter final int magFilter,
		@Wrap final int wrap) {

		return initTexes(new int[n], texTarget, texUnit,
			minFilter, magFilter, wrap);
	}

	/**
	 * テクスチャ名配列を生成(こっちは全部同じテクスチャユニット), クランプ方法はGL_CLAMP_TO_EDGE
	 * @param texIds 最大で32個(GL_MAX_TEXTURE_IMAGE_UNITS以下)
	 * @param texTarget テクスチャのタイプ, GL_TEXTURE_EXTERNAL_OESかGL_TEXTURE_2D
	 * @param texUnit テクスチャユニット
	 * @param filterParam テクスチャの補完方法を指定, min/mag共に同じ値になる, GL_LINEARとかGL_NEAREST
	 * @return
	 */
	public static int[] initTexes(
		@NonNull final int[] texIds,
		@TexTarget final int texTarget, @TexUnit final int texUnit,
		@MinMagFilter final int filterParam) {

		return initTexes(texIds, texTarget, texUnit,
			filterParam, filterParam, GLES20.GL_CLAMP_TO_EDGE);
	}

	/**
	 * テクスチャ名配列を生成(こっちは全部同じテクスチャユニット)
	 * @param texIds テクスチャ名配列
	 * @param texTarget テクスチャのタイプ, GL_TEXTURE_EXTERNAL_OESかGL_TEXTURE_2D
	 * @param texUnit テクスチャユニット
	 * @param minFilter テクスチャの補間方法を指定, GL_LINEARとかGL_NEAREST
	 * @param magFilter テクスチャの補間方法を指定, GL_LINEARとかGL_NEAREST
	 * @param wrap テクスチャのクランプ方法, GL_CLAMP_TO_EDGE等
	 * @return
	 */
	public static int[] initTexes(@NonNull final int[] texIds,
		@TexTarget final int texTarget, @TexUnit final int texUnit,
		@MinMagFilter final int minFilter, @MinMagFilter final int magFilter,
		@Wrap final int wrap) {

		int[] textureUnits = new int[1];
		GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_IMAGE_UNITS, textureUnits, 0);
		final int n = Math.min(texIds.length, textureUnits[0]);
		for (int i = 0; i < n; i++) {
			texIds[i] = initTex(texTarget, texUnit,
				minFilter, magFilter, wrap);
		}
		return texIds;
	}

	/**
	 * delete specific texture
	 */
	public static void deleteTex(final int hTex) {
		if (DEBUG) Log.v(TAG, "deleteTex:");
		final int[] tex = new int[] {hTex};
		GLES20.glDeleteTextures(1, tex, 0);
	}

	/**
	 * delete specific texture
	 */
	public static void deleteTex(@NonNull final int[] tex) {
		if (DEBUG) Log.v(TAG, "deleteTex:");
		GLES20.glDeleteTextures(tex.length, tex, 0);
	}

	public static int loadTextureFromResource(final Context context, final int resId) {
		return loadTextureFromResource(context, resId, null);
	}

	@SuppressLint("NewApi")
	public static int loadTextureFromResource(final Context context, final int resId, final Resources.Theme theme) {
		if (DEBUG) Log.v(TAG, "loadTextureFromResource:");
		// Create an empty, mutable bitmap
		final Bitmap bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
		// get a canvas to paint over the bitmap
		final Canvas canvas = new Canvas(bitmap);
		canvas.drawARGB(0,0,255,0);

		// get a background image from resources
		// note the image format must match the bitmap format
		final Drawable background = ResourcesCompat.getDrawable(context.getResources(), resId, theme);
		background.setBounds(0, 0, 256, 256);
		background.draw(canvas); // draw the background to our bitmap

		final int[] textures = new int[1];

		//Generate one texture pointer...
		GLES20.glGenTextures(1, textures, 0);
		//...and makeCurrent it to our array
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);

		//Create Nearest Filtered Texture
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
			GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
			GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

		//Different possible texture parameters, e.g. GLES20.GL_CLAMP_TO_EDGE
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
			GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
			GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);

		//Use the Android GLUtils to specify a two-dimensional texture image from our bitmap
		android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
		//Clean up
		bitmap.recycle();

		return textures[0];
	}

	public static int createTextureWithTextContent(@NonNull final String text) {
		return createTextureWithTextContent(text, GLES20.GL_TEXTURE0);
	}

	public static int createTextureWithTextContent(
		@NonNull final String text, @TexUnit final int texUnit) {

		if (DEBUG) Log.v(TAG, "createTextureWithTextContent:");
		// Create an empty, mutable bitmap
		final Bitmap bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
		// get a canvas to paint over the bitmap
		final Canvas canvas = new Canvas(bitmap);
		canvas.drawARGB(0,0,255,0);

		// Draw the text
		final Paint textPaint = new Paint();
		textPaint.setTextSize(32);
		textPaint.setAntiAlias(true);
		textPaint.setARGB(0xff, 0xff, 0xff, 0xff);
		// draw the text centered
		canvas.drawText(text, 16, 112, textPaint);

		final int texture = initTex(GLES20.GL_TEXTURE_2D,
			texUnit, GLES20.GL_NEAREST, GLES20.GL_LINEAR, GLES20.GL_REPEAT);

		// Alpha blending
		// GLES20.glEnable(GLES20.GL_BLEND);
		// GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

		// Use the Android GLUtils to specify a two-dimensional texture image from our bitmap
		android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
		// Clean up
		bitmap.recycle();

		return texture;
	}

	/**
	 * load, compile and link shader from Assets files
	 * @param context
	 * @param vss_asset source file name in Assets of vertex shader
	 * @param fss_asset source file name in Assets of fragment shader
	 * @return
	 */
	public static int loadShader(@NonNull final Context context,
		final String vss_asset, final String fss_asset) {

		int program;
		try {
			final String vss = AssetsHelper.loadString(context.getAssets(), vss_asset);
			final String fss = AssetsHelper.loadString(context.getAssets(), vss_asset);
			program = loadShader(vss, fss);
		} catch (final IOException e) {
			program = 0;
		}
		return program;
	}

	/**
	 * load, compile and link shader
	 * @param vss source of vertex shader
	 * @param fss source of fragment shader
	 * @return
	 */
	public static int loadShader(final String vss, final String fss) {
		if (DEBUG) Log.v(TAG, "loadShader:");
		final int[] compiled = new int[1];
		// 頂点シェーダーをコンパイル
		final int vs = loadShader(GLES20.GL_VERTEX_SHADER, vss);
		if (vs == 0) {
			Log.d(TAG, "loadShader:failed to compile vertex shader,\n" + vss);
			return 0;
		}
		// フラグメントシェーダーをコンパイル
		int fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fss);
		if (fs == 0) {
			Log.d(TAG, "loadShader:failed to compile fragment shader,\n" + fss);
			return 0;
		}
		// リンク
		final int program = GLES20.glCreateProgram();
		checkGlError("glCreateProgram");
		if (program == 0) {
			Log.e(TAG, "Could not create program");
		}
		GLES20.glAttachShader(program, vs);
		checkGlError("glAttachShader");
		GLES20.glAttachShader(program, fs);
		checkGlError("glAttachShader");
		GLES20.glLinkProgram(program);
		final int[] linkStatus = new int[1];
		GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
		if (linkStatus[0] != GLES20.GL_TRUE) {
			Log.e(TAG, "Could not link program: ");
			Log.e(TAG, GLES20.glGetProgramInfoLog(program));
			GLES20.glDeleteProgram(program);
			return 0;
		}
		return program;
	}

	/**
	  * Compiles the provided shader source.
	  *
	  * @return A handle to the shader, or 0 on failure.
	  */
	public static int loadShader(final int shaderType, final String source) {
		int shader = GLES20.glCreateShader(shaderType);
		checkGlError("glCreateShader type=" + shaderType);
		GLES20.glShaderSource(shader, source);
		GLES20.glCompileShader(shader);
		final int[] compiled = new int[1];
		GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
		if (compiled[0] == 0) {
			Log.e(TAG, "Could not compile shader " + shaderType + ":");
			Log.e(TAG, " " + GLES20.glGetShaderInfoLog(shader));
			GLES20.glDeleteShader(shader);
			shader = 0;
		}
		return shader;
	 }

	/**
	 * Checks to see if the location we obtained is valid.  GLES returns -1 if a label
	 * could not be found, but does not set the GL error.
	 * <p>
	 * Throws a RuntimeException if the location is invalid.
	 */
	public static void checkLocation(final int location, final String label) {
		if (location < 0) {
			throw new RuntimeException("Unable to locate '" + label + "' in program");
		}
	}

	/**
	 * バッファーオブジェクトを生成＆データをセットしてバッファー名を返す
	 * @param target GL_ARRAY_BUFFERまたはGL_ELEMENT_ARRAY_BUFFER
	 * @param data
	 * @param usage GL_STATIC_DRAW, GL_STREAM_DRAW, GL_DYNAMIC_DRAW
	 * @return
	 */
	public static int createBuffer(final int target, @NonNull final FloatBuffer data, final int usage) {
		final int[] ids = new int[1];
		GLES20.glGenBuffers(1, ids, 0);
		checkGlError("glGenBuffers");
		GLES20.glBindBuffer(target, ids[0]);
		checkGlError("glBindBuffer");
		GLES20.glBufferData(target, SIZEOF_FLOAT_BYTES * data.limit(), data, usage);
		checkGlError("glBufferData");
		GLES20.glBindBuffer(target, 0);
		return ids[0];
	}

	/**
	 * バッファーオブジェクトを破棄する
	 * @param bufId
	 */
	public static void deleteBuffer(final int bufId) {
		deleteBuffer(new int[] {bufId});
	}

	/**
	 * バッファーオブジェクトを破棄する
	 * @param bufIds
	 */
	public static void deleteBuffer(@NonNull final int[] bufIds) {
		GLES20.glDeleteBuffers(bufIds.length, bufIds, 0);
	}
}
