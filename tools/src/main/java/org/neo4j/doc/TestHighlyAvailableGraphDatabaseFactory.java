/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.doc;

import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.graphdb.factory.HighlyAvailableGraphDatabaseFactory;
import org.neo4j.kernel.configuration.BoltConnector;

import java.util.Map;

import static java.util.Collections.unmodifiableMap;
import static org.neo4j.helpers.collection.MapUtil.stringMap;

public class TestHighlyAvailableGraphDatabaseFactory extends HighlyAvailableGraphDatabaseFactory {

    public static final Map<String,String> CONFIG_FOR_SINGLE_JVM_CLUSTER = unmodifiableMap( stringMap(
            GraphDatabaseSettings.pagecache_memory.name(), "8m",
            GraphDatabaseSettings.shutdown_transaction_end_timeout.name(), "1s",
            new BoltConnector( "bolt" ).type.name(), "BOLT",
            new BoltConnector( "bolt" ).enabled.name(), "false"
    ) );

    public TestHighlyAvailableGraphDatabaseFactory() {
    }

    protected void configure(GraphDatabaseBuilder builder) {
        super.configure(builder);
        builder.setConfig(CONFIG_FOR_SINGLE_JVM_CLUSTER);
        builder.setConfig(GraphDatabaseSettings.store_internal_log_level, "DEBUG");
    }

}
