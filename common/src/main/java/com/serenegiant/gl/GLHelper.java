package com.serenegiant.gl;
/*
 * libcommon
 * utility/helper classes for myself
 *
 * Copyright (c) 2014-2023 saki t_saki@serenegiant.com
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
import android.graphics.drawable.Drawable;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

import com.serenegiant.system.Stacktrace;
import com.serenegiant.utils.AssetsHelper;

import java.io.IOException;
import java.nio.FloatBuffer;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;

import static com.serenegiant.utils.BufferHelper.SIZEOF_FLOAT_BYTES;

/**
 * OpenGL|ES2/3用のヘルパークラス
 * @deprecated GLUtilsの同名メソッドを使うこと
 */
@SuppressWarnings("deprecation")
@Deprecated
public final class GLHelper implements GLConst {
	private static final boolean DEBUG = false;	// FIXME 実働時はfalseにすること
	private static final String TAG = GLHelper.class.getSimpleName();

	private GLHelper() {
		// インスタンス化をエラーにするためにデフォルトコンストラクタをprivateに
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
	public static int[] initTexes(final int n,
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
	public static int[] initTexes(@NonNull final int[] texIds,
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
	public static int[] initTexes(final int n,
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
	public static int[] initTexes(@NonNull final int[] texIds,
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
	public static int[] initTexes(final int n,
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
	public static int[] initTexes(@NonNull final int[] texIds,
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
		GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
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
		GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
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
