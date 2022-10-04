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

import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;

/**
 * GLPipelineのインターフェースメソッドの基本的機能を実装＆中継をするだけのGLPipeline実装
 */
public class ProxyPipeline implements GLPipeline {
	private static final boolean DEBUG = false;	// set false on production
	private static final String TAG = ProxyPipeline.class.getSimpleName();

	private static final int DEFAULT_WIDTH = 640;
	private static final int DEFAULT_HEIGHT = 480;

	@NonNull
	protected final Object mSync = new Object();
	private int mWidth, mHeight;
	@Nullable
	private GLPipeline mParent;
	@Nullable
	private GLPipeline mPipeline;
	private volatile boolean mReleased = false;

	/**
	 * デフォルトコンストラクタ
	 */
	public ProxyPipeline() {
		this(DEFAULT_WIDTH, DEFAULT_HEIGHT);
	}

	/**
	 * コンストラクタ
	 * @param width
	 * @param height
	 */
	protected ProxyPipeline(final int width, final int height) {
		if (DEBUG) Log.v(TAG, "コンストラクタ:");
		if ((width > 0) && (height > 0)) {
			mWidth = width;
			mHeight = height;
		} else {
			mWidth = DEFAULT_WIDTH;
			mHeight = DEFAULT_HEIGHT;
		}
	}

	@Override
	protected void finalize() throws Throwable {
		try {
			release();
		} finally {
			super.finalize();
		}
	}

	@Override
	public final void release() {
		if (!mReleased) {
			mReleased = true;
			internalRelease();
		}
	}

	@CallSuper
	protected void internalRelease() {
		if (DEBUG) Log.v(TAG, "internalRelease:" + this);
		mReleased = true;
		final GLPipeline pipeline;
		synchronized (mSync) {
			pipeline = mPipeline;
			mPipeline = null;
			mParent = null;
		}
		if (pipeline != null) {
			pipeline.release();
		}
	}

	@CallSuper
	@Override
	public void resize(final int width, final int height) throws IllegalStateException {
		if (!mReleased) {
			mWidth = width;
			mHeight = height;
			final GLPipeline pipeline = getPipeline();
			if (pipeline != null) {
				pipeline.resize(width, height);
			}
		} else {
			throw new IllegalStateException("already released!");
		}
	}

	/**
	 * GLPipelineの実装
	 * オブジェクトが有効かどうかを取得
	 * @return
	 */
	@Override
	public boolean isValid() {
		return !mReleased;
	}

	/**
	 * GLPipelineの実装
	 * パイプラインチェーンに組み込まれているかどうかを取得
	 * @return
	 */
	public boolean isActive() {
		synchronized (mSync) {
			// 破棄されていない
			// && (親と繋がっている || GLPipelineSourceで子と繋がっている)
			return !mReleased && (mParent != null);
		}
	}

	@Override
	public int getWidth() {
		return mWidth;
	}

	@Override
	public int getHeight() {
		return mHeight;
	}

	/**
	 * 呼び出し元のGLPipelineインスタンスを設定する
	 * @param parent
	 */
	@CallSuper
	public void setParent(@Nullable final GLPipeline parent) {
		if (!mReleased) {
			if (DEBUG) Log.v(TAG, "setParent:" + this + ",parent=" + parent);
			synchronized (mSync) {
				mParent = parent;
			}
		} else {
			throw new IllegalStateException("already released!");
		}
	}
	@Nullable
	@Override
	public GLPipeline getParent() {
		synchronized (mSync) {
			return mParent;
		}
	}

	@CallSuper
	@Override
	public void setPipeline(@Nullable final GLPipeline pipeline) {
		if (DEBUG) Log.v(TAG, "setPipeline:" + this + ",pipeline=" + pipeline);
		if (!mReleased) {
			synchronized (mSync) {
				mPipeline = pipeline;
			}
			if (pipeline != null) {
				pipeline.setParent(this);
				pipeline.resize(mWidth, mHeight);
			}
		} else {
			throw new IllegalStateException("already released!");
		}
	}

	@Nullable
	public GLPipeline getPipeline() {
		synchronized (mSync) {
			return mPipeline;
		}
	}

	@CallSuper
	@Override
	public void remove() {
		if (DEBUG) Log.v(TAG, "remove:" + this);
		final GLPipeline first = GLPipeline.findFirst(this);
		GLPipeline parent;
		synchronized (mSync) {
			parent = mParent;
			if (mParent instanceof DistributePipeline) {
				// 親がDistributePipelineの時は自分を取り除くだけ
				((DistributePipeline) mParent).removePipeline(this);
			} else if (mParent != null) {
				// その他のGLPipelineの時は下流を繋ぐ
				mParent.setPipeline(mPipeline);
			}
			mParent = null;
			mPipeline = null;
		}
		if (first != this) {
			GLPipeline.validatePipelineChain(first);
			parent.refresh();
		}
	}

	@CallSuper
	@Override
	public void onFrameAvailable(
		final boolean isOES, final int texId,
		@NonNull @Size(min=16) final float[] texMatrix) {

		if (!mReleased) {
			final GLPipeline pipeline;
			synchronized (mSync) {
				pipeline = mPipeline;
			}
			if (pipeline != null) {
				pipeline.onFrameAvailable(isOES, texId, texMatrix);
			}
		}
	}

	@Override
	public void refresh() {
		if (!mReleased) {
			final GLPipeline pipeline;
			synchronized (mSync) {
				pipeline = mPipeline;
			}
			if (pipeline != null) {
				pipeline.refresh();
			}
		}
	}
}
