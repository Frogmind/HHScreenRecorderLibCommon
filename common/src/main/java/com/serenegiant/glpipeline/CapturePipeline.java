package com.serenegiant.glpipeline;
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

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.util.Log;

import com.serenegiant.gl.GLSurface;
import com.serenegiant.gl.GLUtils;
import com.serenegiant.utils.Pool;
import com.serenegiant.utils.ThreadPool;

import java.nio.ByteBuffer;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 静止画キャプチャ機能を追加したProxyPipeline実装
 */
public class CapturePipeline extends ProxyPipeline {
	private static final boolean DEBUG = true;	// set false on production
	private static final String TAG = CapturePipeline.class.getSimpleName();

	/**
	 * キャプチャ時のコールバックリスナー
	 */
	public interface Callback {
		@AnyThread
		public void onCapture(@NonNull final Bitmap bitmap);
	}

	@NonNull
	private final Object mSync = new Object();
	@NonNull
	private final Callback mCallback;

	/**
	 * キャプチャ回数
	 * -1: 無制限, 0: 無効, 1以上: 指定回数
	 */
	private int mNumCaptures;
	/**
	 * キャプチャ周期[ミリ秒]
	 */
	private long mIntervalsMs;

	/**
	 * キャプチャした回数
	 */
	private int mCaptureCnt;
	/**
	 * キャプチャしたシステム時刻[ミリ秒]
	 */
	private long mLastCaptureMs;
	/**
	 * 映像読み取り用のワークバッファ
	 */
	@Nullable
	private ByteBuffer mBuffer;

	private final Pool<Bitmap> mPool = new Pool<Bitmap>(0, 4) {
		@NonNull
		@Override
		protected Bitmap createObject(@Nullable final Object... args) {
			final int w = (int)args[0];
			final int h = (int)args[1];
			return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
		}

		@Nullable
		@Override
		public Bitmap obtain(@Nullable final Object... args) {
			final int w = (int)args[0];
			final int h = (int)args[1];
			Bitmap result = super.obtain(args);
			while ((result != null)
				&& (result.isRecycled()
					|| (result.getWidth() != w)
					|| (result.getHeight() != h))) {
				// サイズが違う・リサイクルされてしまっているBitmapは返さない
				release(result);
				result = super.obtain(args);
			}
			return result;
		}
	};

	/**
	 * コンストラクタ
	 */
	public CapturePipeline(@NonNull final Callback callback) {
		super();
		mCallback = callback;
		mCaptureCnt = mNumCaptures = 0;
		mLastCaptureMs = mIntervalsMs = 0L;
	}

	/**
	 * 1回だけキャプチャ要求
	 */
	public void trigger() {
		trigger(1, 0L);
	}

	/**
	 * 指定した条件でキャプチャ要求
	 * @param numCaptures キャプチャ回数, -1: 無制限, 0: 無効, 1以上: 指定回数
	 * @param intervalsMs 複数回キャプチャする場合の周期[ミリ秒]
	 */
	public void trigger(final int numCaptures, final long intervalsMs) {
		synchronized (mSync) {
			mCaptureCnt = 0;
			mNumCaptures = numCaptures;
			mIntervalsMs = intervalsMs;
			mLastCaptureMs = 0L;
		}
	}

	@Override
	public void onFrameAvailable(final boolean isOES, final int texId, @NonNull final float[] texMatrix) {
		super.onFrameAvailable(isOES, texId, texMatrix);
		// キャプチャするかどうかを判定
		final long current = System.currentTimeMillis();
		final boolean needCapture;
		synchronized (mSync) {
			needCapture = (mNumCaptures != 0) && (mCaptureCnt < mNumCaptures)
				&& (current - mLastCaptureMs > mIntervalsMs);
			if (needCapture) {
				mLastCaptureMs = current;
				mCaptureCnt++;
			}
		}
		if (needCapture) {
			// キャプチャする場合
			final int w = getWidth();
			final int h = getHeight();
			final Bitmap bitmap = mPool.obtain(w, h);
			if (bitmap != null) {
				// GLSurfaceを経由してテクスチャを読み取る
				final GLSurface surface = GLSurface.wrap(isOES, GLES20.GL_TEXTURE0, texId, w, h);
				surface.makeCurrent();
				@NonNull
				final ByteBuffer buffer = GLUtils.glReadPixels(mBuffer, w, h);
				bitmap.copyPixelsFromBuffer(buffer);
				mBuffer = buffer;
				// コールバックをワーカースレッド上で呼び出す
				ThreadPool.queueEvent(() -> {
					try {
						mCallback.onCapture(bitmap);
					} catch (final Exception e) {
						Log.w(TAG, e);
					} finally {
						if (bitmap.isRecycled()) {
							mPool.release(bitmap);
						} else {
							mPool.recycle(bitmap);
						}
					}
				});
			}
		}
	}
}