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

package com.nfsdb.ql.ops;

import com.nfsdb.factory.configuration.ColumnMetadata;
import com.nfsdb.factory.configuration.RecordColumnMetadata;
import com.nfsdb.ql.AggregatorFunction;
import com.nfsdb.ql.Record;
import com.nfsdb.ql.impl.map.MapRecordValueInterceptor;
import com.nfsdb.ql.impl.map.MapValues;
import com.nfsdb.std.ObjList;
import com.nfsdb.std.ObjectFactory;
import com.nfsdb.store.ColumnType;

public final class VwapAggregator extends AbstractBinaryOperator implements AggregatorFunction, MapRecordValueInterceptor {

    public static final ObjectFactory<Function> FACTORY = new ObjectFactory<Function>() {
        @Override
        public Function newInstance() {
            return new VwapAggregator();
        }
    };

    private final static ColumnMetadata INTERNAL_COL_AMOUNT = new ColumnMetadata().setName("$sumAmt").setType(ColumnType.DOUBLE);
    private final static ColumnMetadata INTERNAL_COL_QUANTITY = new ColumnMetadata().setName("$sumQty").setType(ColumnType.DOUBLE);
    private int sumAmtIdx;
    private int sumQtyIdx;
    private int vwap;

    private VwapAggregator() {
        super(ColumnType.DOUBLE);
    }

    @Override
    public void beforeRecord(MapValues values) {
        values.putDouble(vwap, values.getDouble(sumAmtIdx) / values.getDouble(sumQtyIdx));
    }

    @Override
    public void calculate(Record rec, MapValues values) {
        double price = lhs.getDouble(rec);
        double quantity = rhs.getDouble(rec);
        if (values.isNew()) {
            values.putDouble(sumAmtIdx, price * quantity);
            values.putDouble(sumQtyIdx, quantity);
        } else {
            values.putDouble(sumAmtIdx, values.getDouble(sumAmtIdx) + price * quantity);
            values.putDouble(sumQtyIdx, values.getDouble(sumQtyIdx) + quantity);
        }
    }

    @Override
    public void prepare(ObjList<RecordColumnMetadata> columns, int offset) {
        columns.add(INTERNAL_COL_AMOUNT);
        columns.add(INTERNAL_COL_QUANTITY);
        columns.add(new ColumnMetadata().setName(getName()).setType(ColumnType.DOUBLE));
        sumAmtIdx = offset;
        sumQtyIdx = offset + 1;
        vwap = offset + 2;
    }
}
