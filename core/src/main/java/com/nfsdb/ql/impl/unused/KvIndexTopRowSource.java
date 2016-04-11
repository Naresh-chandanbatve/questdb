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
import com.nfsdb.ql.KeyCursor;
import com.nfsdb.ql.KeySource;
import com.nfsdb.ql.PartitionSlice;
import com.nfsdb.ql.RowCursor;
import com.nfsdb.ql.impl.AbstractRowSource;
import com.nfsdb.ql.impl.JournalRecord;
import com.nfsdb.ql.ops.VirtualColumn;
import com.nfsdb.store.IndexCursor;
import com.nfsdb.store.KVIndex;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;


/**
 * Streams rowids on assumption that {@link #keySource} produces only one key.
 * This is used in nested-loop join where "slave" source is scanned for one key at a time.
 */
@SuppressFBWarnings({"EXS_EXCEPTION_SOFTENING_NO_CHECKED"})
public class KvIndexTopRowSource extends AbstractRowSource {

    private final String column;
    private final VirtualColumn filter;
    private final KeySource keySource;
    private JournalRecord rec;

    private KVIndex index;
    private KeyCursor keyCursor;
    private long lo;
    private long hi;

    public KvIndexTopRowSource(String column, KeySource keySource, VirtualColumn filter) {
        this.column = column;
        this.keySource = keySource;
        this.filter = filter;
    }

    @Override
    public void configure(JournalMetadata metadata) {
        this.rec = new JournalRecord(metadata);
    }

    @Override
    public RowCursor prepareCursor(PartitionSlice slice) {
        try {
            this.index = slice.partition.getIndexForColumn(column);
            this.lo = slice.lo - 1;
            this.hi = slice.calcHi ? slice.partition.open().size() : slice.hi + 1;
            this.keyCursor = keySource.prepareCursor();
            this.rec.partition = slice.partition;
            return this;
        } catch (JournalException e) {
            throw new JournalRuntimeException(e);
        }
    }

    @Override
    public void reset() {
        keySource.reset();
    }

    @Override
    public boolean hasNext() {

        if (!keyCursor.hasNext()) {
            return false;
        }

        IndexCursor indexCursor = index.cursor(keyCursor.next());
        while (indexCursor.hasNext()) {
            rec.rowid = indexCursor.next();
            if (rec.rowid > lo && rec.rowid < hi && (filter == null || filter.getBool(rec))) {
                return true;
            }

            if (rec.rowid <= lo) {
                break;
            }
        }

        return false;
    }

    @Override
    public long next() {
        return rec.rowid;
    }
}
