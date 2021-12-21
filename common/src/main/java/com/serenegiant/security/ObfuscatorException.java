package com.serenegiant.security;
/*
 * libcommon
 * utility/helper classes for myself
 *
 * Copyright (c) 2014-2021 saki t_saki@serenegiant.com
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

/**
 * 暗号化・復号時のエラーを通知するためのException実装
 */
public class ObfuscatorException extends Exception {
	private static final long serialVersionUID = -437726590003072651L;

	public ObfuscatorException() {
	}

	public ObfuscatorException(final String message) {
		super(message);
	}

	public ObfuscatorException(final String message, final Throwable cause) {
		super(message, cause);
	}

	public ObfuscatorException(final Throwable cause) {
		super(cause);
	}
}
