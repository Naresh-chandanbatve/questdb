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

package com.nfsdb.ql.impl.unused;

import com.nfsdb.ex.JournalException;
import com.nfsdb.ex.JournalRuntimeException;
import com.nfsdb.factory.configuration.JournalMetadata;
import com.nfsdb.ql.PartitionSlice;
import com.nfsdb.ql.RowCursor;
import com.nfsdb.ql.RowSource;
import com.nfsdb.ql.StorageFacade;
import com.nfsdb.ql.impl.AbstractRowSource;
import com.nfsdb.std.IntHashSet;
import com.nfsdb.store.FixedColumn;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Takes stream of rowids, converts them to int values of FixedColumn and
 * returns rowids for non-repeated int values. Rowids returned on first in - first out basis.
 * <p>
 * One of use cases might be streaming of journal in reverse chronological order (latest rows first)
 * via this filter to receive last records for every value of given column.
 */
public class DistinctSymbolRowSource extends AbstractRowSource {

    private final RowSource delegate;
    private final String symbol;
    private final IntHashSet set = new IntHashSet();
    private FixedColumn column;
    private int columnIndex = -1;
    private RowCursor cursor;
    private long rowid;

    public DistinctSymbolRowSource(RowSource delegate, String symbol) {
        this.delegate = delegate;
        this.symbol = symbol;
    }

    @Override
    public void configure(JournalMetadata metadata) {
        delegate.configure(metadata);
        columnIndex = metadata.getColumnIndex(symbol);
    }

    @SuppressFBWarnings("EXS_EXCEPTION_SOFTENING_NO_CHECKED")
    @Override
    public RowCursor prepareCursor(PartitionSlice slice) {
        try {
            column = slice.partition.open().fixCol(columnIndex);
            cursor = delegate.prepareCursor(slice);
            return this;
        } catch (JournalException e) {
            throw new JournalRuntimeException(e);
        }
    }

    @Override
    public void reset() {
        delegate.reset();
    }

    @Override
    public boolean hasNext() {
        while (cursor.hasNext()) {
            long rowid = cursor.next();
            if (set.add(column.getInt(rowid))) {
                this.rowid = rowid;
                return true;
            }
        }
        return false;
    }

    @Override
    public long next() {
        return rowid;
    }

    @Override
    public void prepare(StorageFacade storageFacade) {
        delegate.prepare(storageFacade);
        set.clear();
    }
}
