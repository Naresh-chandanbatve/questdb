/*******************************************************************************
 *  _  _ ___ ___     _ _
 * | \| | __/ __| __| | |__
 * | .` | _|\__ \/ _` | '_ \
 * |_|\_|_| |___/\__,_|_.__/
 *
 * Copyright (C) 2014-2016 Appsicle
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * As a special exception, the copyright holders give permission to link the
 * code of portions of this program with the OpenSSL library under certain
 * conditions as described in each individual source file and distribute
 * linked combinations including the program with the OpenSSL library. You
 * must comply with the GNU Affero General Public License in all respects for
 * all of the code used other than as permitted herein. If you modify file(s)
 * with this exception, you may extend this exception to your version of the
 * file(s), but you are not obligated to do so. If you do not wish to do so,
 * delete this exception statement from your version. If you delete this
 * exception statement from all source files in the program, then also delete
 * it in the license file.
 *
 ******************************************************************************/

package com.nfsdb.net.http;

import com.nfsdb.ex.HeadersTooLargeException;
import com.nfsdb.ex.MalformedHeaderException;
import com.nfsdb.log.Log;
import com.nfsdb.log.LogFactory;
import com.nfsdb.misc.Chars;
import com.nfsdb.misc.Misc;
import com.nfsdb.misc.Numbers;
import com.nfsdb.misc.Unsafe;
import com.nfsdb.std.CharSequenceObjHashMap;
import com.nfsdb.std.DirectByteCharSequence;
import com.nfsdb.std.Mutable;
import com.nfsdb.std.ObjectPool;

import java.io.Closeable;

public class RequestHeaderBuffer implements Mutable, Closeable {
    private static final Log LOG = LogFactory.getLog(RequestHeaderBuffer.class);
    private final ObjectPool<DirectByteCharSequence> pool;
    private final CharSequenceObjHashMap<CharSequence> headers = new CharSequenceObjHashMap<>();
    private final CharSequenceObjHashMap<CharSequence> urlParams = new CharSequenceObjHashMap<>();
    private final long hi;
    private long _wptr;
    private long headerPtr;
    private CharSequence method;
    private CharSequence url;
    private CharSequence methodLine;
    private boolean needMethod;
    private long _lo;
    private CharSequence n;
    private boolean incomplete;
    private CharSequence contentType;
    private CharSequence boundary;
    private CharSequence contentDispositionName;
    private CharSequence contentDispositionFilename;
    private boolean m = true;
    private boolean u = true;
    private boolean q = false;

    public RequestHeaderBuffer(int size, ObjectPool<DirectByteCharSequence> pool) {
        int sz = Numbers.ceilPow2(size);
        this.headerPtr = Unsafe.getUnsafe().allocateMemory(sz);
        this._wptr = headerPtr;
        this.hi = this.headerPtr + sz;
        this.pool = pool;
        clear();
    }

    @Override
    public final void clear() {
        this.needMethod = true;
        this._wptr = this._lo = this.headerPtr;
        this.incomplete = true;
        this.headers.clear();
        this.method = null;
        this.url = null;
        this.n = null;
        this.contentType = null;
        this.boundary = null;
        this.contentDispositionName = null;
        this.contentDispositionFilename = null;
        this.urlParams.clear();
        this.m = true;
        this.u = true;
        this.q = false;
    }

    @Override
    public void close() {
        if (this.headerPtr != 0) {
            Unsafe.getUnsafe().freeMemory(this.headerPtr);
            this.headerPtr = 0;
        }
    }

    public CharSequence get(CharSequence name) {
        return headers.get(name);
    }

    public CharSequence getBoundary() {
        return boundary;
    }

    public CharSequence getContentDispositionFilename() {
        return contentDispositionFilename;
    }

    public CharSequence getContentDispositionName() {
        return contentDispositionName;
    }

    public CharSequence getContentType() {
        return contentType;
    }

    public CharSequence getMethod() {
        return method;
    }

    public CharSequence getMethodLine() {
        return methodLine;
    }

    public CharSequence getUrl() {
        return url;
    }

    public CharSequence getUrlParam(CharSequence name) {
        return urlParams.get(name);
    }

    public boolean isIncomplete() {
        return incomplete;
    }

    public int size() {
        return headers.size();
    }

    public long write(long ptr, int len, boolean _method) throws HeadersTooLargeException, MalformedHeaderException {
        if (_method && needMethod) {
            int l = parseMethod(ptr, len);
            len -= l;
            ptr += l;
        }

        long p = ptr;
        long hi = p + len;

        DirectByteCharSequence v;

        while (p < hi) {
            if (_wptr == this.hi) {
                throw HeadersTooLargeException.INSTANCE;
            }

            char b = (char) Unsafe.getUnsafe().getByte(p++);

            if (b == '\r') {
                continue;
            }

            Unsafe.getUnsafe().putByte(_wptr++, (byte) b);

            switch (b) {
                case ':':
                    if (n == null) {
                        n = pool.next().of(_lo, _wptr - 1);
                        _lo = _wptr + 1;
                    }
                    break;
                case '\n':
                    if (n == null) {
                        incomplete = false;
                        parseKnownHeaders();
                        return p;
                    }
                    v = pool.next().of(_lo, _wptr - 1);
                    _lo = _wptr;
                    headers.put(n, v);
                    n = null;
                    break;
                default:
                    break;
            }
        }

        return p;
    }

    private static DirectByteCharSequence unquote(DirectByteCharSequence that) throws MalformedHeaderException {
        int len = that.length();
        if (len == 0) {
            LOG.error().$("Zero-length mandatory field").$();
            // zero length mandatory field
            throw MalformedHeaderException.INSTANCE;
        }

        if (that.charAt(0) == '"') {
            if (that.charAt(len - 1) == '"') {
                return that.of(that.getLo() + 1, that.getHi() - 1);
            } else {
                LOG.error().$("Unclosed quote").$();
                // unclosed quote
                throw MalformedHeaderException.INSTANCE;
            }
        } else {
            return that;
        }
    }

    private void parseContentDisposition() throws MalformedHeaderException {
        CharSequence contentDisposition = get("Content-Disposition");
        if (contentDisposition == null) {
            return;
        }

        long p = ((DirectByteCharSequence) contentDisposition).getLo();
        long _lo = p;
        long hi = ((DirectByteCharSequence) contentDisposition).getHi();

        boolean expectFormData = true;
        boolean swallowSpace = true;

        DirectByteCharSequence name = null;

        while (p <= hi) {
            char b = (char) Unsafe.getUnsafe().getByte(p++);

            if (b == ' ' && swallowSpace) {
                _lo = p;
                continue;
            }

            if (p > hi || b == ';') {
                if (expectFormData) {
                    _lo = p;
                    expectFormData = false;
                    continue;
                }

                if (name == null) {
                    LOG.error().$("Malformed content-disposition header").$();
                    throw MalformedHeaderException.INSTANCE;
                }

                if (Chars.equals("name", name)) {
                    this.contentDispositionName = unquote(pool.next().of(_lo, p - 1));
                    swallowSpace = true;
                    _lo = p;
                    continue;
                }

                if (Chars.equals("filename", name)) {
                    this.contentDispositionFilename = unquote(pool.next().of(_lo, p - 1));
                    _lo = p;
                    continue;
                }

                if (p > hi) {
                    break;
                }
            } else if (b == '=') {
                name = name == null ? pool.next().of(_lo, p - 1) : name.of(_lo, p - 1);
                _lo = p;
                swallowSpace = false;
            }
        }
    }

    private void parseContentType() throws MalformedHeaderException {
        CharSequence seq = get("Content-Type");
        if (seq == null) {
            return;
        }

        long p = ((DirectByteCharSequence) seq).getLo();
        long _lo = p;
        long hi = ((DirectByteCharSequence) seq).getHi();

        DirectByteCharSequence name = null;
        boolean contentType = true;
        boolean swallowSpace = true;

        while (p <= hi) {
            char b = (char) Unsafe.getUnsafe().getByte(p++);

            if (b == ' ' && swallowSpace) {
                _lo = p;
                continue;
            }

            if (p > hi || b == ';') {
                if (contentType) {
                    this.contentType = pool.next().of(_lo, p - 1);
                    _lo = p;
                    contentType = false;
                    continue;
                }

                if (name == null) {
                    LOG.error().$("Malformed content-type header").$();
                    throw MalformedHeaderException.INSTANCE;
                }

                if (Chars.equals("encoding", name)) {
                    // would be encoding, but we don't use it yet
//                    this.encoding = pool.next().of(_lo, p - 1);
                    _lo = p;
                    continue;
                }

                if (Chars.equals("boundary", name)) {
                    this.boundary = pool.next().of(_lo, p - 1);
                    _lo = p;
                    continue;
                }

                if (p > hi) {
                    break;
                }
            } else if (b == '=') {
                name = name == null ? pool.next().of(_lo, p - 1) : name.of(_lo, p - 1);
                _lo = p;
                swallowSpace = false;
            }
        }
    }

    private void parseKnownHeaders() throws MalformedHeaderException {
        parseContentType();
        parseContentDisposition();
    }

    private int parseMethod(long lo, int len) throws HeadersTooLargeException {
        long p = lo;
        long hi = lo + len;

        while (p < hi) {
            if (_wptr == this.hi) {
                throw HeadersTooLargeException.INSTANCE;
            }

            char b = (char) Unsafe.getUnsafe().getByte(p++);

            if (b == '\r') {
                continue;
            }

            Unsafe.getUnsafe().putByte(_wptr++, (byte) b);

            switch (b) {
                case ' ':
                    if (m) {
                        method = pool.next().of(_lo, _wptr - 1);
                        _lo = _wptr;
                        m = false;
                    } else if (u) {
                        url = pool.next().of(_lo, _wptr - 1);
                        u = false;
                        _lo = _wptr;
                    } else if (q) {
                        Misc.urlDecode(_lo, _wptr - 1, urlParams, pool);
                        q = false;
                        _lo = _wptr;
                    }
                    break;
                case '?':
                    url = pool.next().of(_lo, _wptr - 1);
                    u = false;
                    q = true;
                    _lo = _wptr;
                    break;
                case '\n':
                    methodLine = pool.next().of(((DirectByteCharSequence) method).getLo(), _wptr - 1);
                    needMethod = false;
                    this._lo = _wptr;
                    return (int) (p - lo);
                default:
                    break;
            }
        }
        return (int) (p - lo);
    }
}
