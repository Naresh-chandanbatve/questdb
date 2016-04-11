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

package com.nfsdb.model.configuration;

import com.nfsdb.PartitionType;
import com.nfsdb.factory.configuration.JournalConfigurationBuilder;
import com.nfsdb.model.*;

import java.util.concurrent.TimeUnit;

public class ModelConfiguration {

    public static final JournalConfigurationBuilder MAIN = new JournalConfigurationBuilder() {{
        $(Quote.class).recordCountHint(10000)
                .partitionBy(PartitionType.MONTH)
                .lag(12, TimeUnit.HOURS)
                .location("quote")
                .keyColumn("sym")
                .$sym("sym").index().valueCountHint(15)
                .$sym("ex").index().valueCountHint(5)
                .$sym("mode")
                .$ts()
        ;

        $(Trade.class).recordCountHint(10000)
                .partitionBy(PartitionType.MONTH)
                .$sym("sym").valueCountHint(14)
                .$sym("ex").valueCountHint(5)
                .$sym("cond").valueCountHint(30)
                .$ts()
        ;

        $(RDFNode.class).recordCountHint(10000)
                .$sym("subj").index().valueCountHint(12000)
                .$sym("subjType").index().valueCountHint(5)
                .$sym("predicate").index().valueCountHint(5)
                .$sym("obj").sameAs("subj").index().valueCountHint(5)
                .$sym("objType").sameAs("subjType").index().valueCountHint(5)
                .$ts()
        ;

        $(TestEntity.class).recordCountHint(10000)
//                .partitionBy(PartitionType.MONTH)
                .keyColumn("sym")
                .$sym("sym").index().valueCountHint(15)
                .$ts()
        ;

        $(Band.class).recordCountHint(10000)
                .$sym("name").index().valueCountHint(1200)
                .$sym("type").valueCountHint(10)
                .$bin("image").size(10000)
                .$ts()
        ;

        $(Album.class)
                .$sym("band").index()
                .$sym("name").index().valueCountHint(1000000)
                .$ts("releaseDate");
    }};
}
