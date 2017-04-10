package org.neo4j.doc.tools;

import org.neo4j.cluster.ClusterSettings;
import org.neo4j.doc.AsciiDocListGenerator;
import org.neo4j.doc.JmxItem;
import org.neo4j.doc.TestHighlyAvailableGraphDatabaseFactory;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.graphdb.factory.HighlyAvailableGraphDatabaseFactory;
import org.neo4j.kernel.configuration.BoltConnector;

import javax.management.Descriptor;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JmxTool {

    private final String dbDir = "target/tmp";

    private static final String IFDEF_HTMLOUTPUT = "ifndef::nonhtmloutput[]\n";
    private static final String IFDEF_NONHTMLOUTPUT = "ifdef::nonhtmloutput[]\n";
    private static final String ENDIF = "endif::nonhtmloutput[]\n";

    private static final String BEAN_NAME = "name";
    private static final String BEAN_NAME0 = "name0";
    private static final List<String> QUERIES = Collections.singletonList( "org.neo4j:*" );
    private static final Set<String> EXCLUDES = Stream.of("JMX Server").collect(Collectors.toSet());

    private static final String JAVADOC_URL = "link:javadocs/";
    private static final Map<String, String> TYPES = new HashMap<String, String>() {
        {
            put( "java.lang.String", "String" );
            put( "java.util.List", "List (java.util.List)" );
            put( "java.util.Date", "Date (java.util.Date)" );
        }
    };

    private static GraphDatabaseService db;

    public JmxTool() {
        File storeDir = new File(dbDir);
        GraphDatabaseBuilder builder = new TestHighlyAvailableGraphDatabaseFactory().newEmbeddedDatabaseBuilder(storeDir);
        db = builder.setConfig( ClusterSettings.server_id, "1" )
                .setConfig( "jmx.port", "9913" )
                .setConfig( ClusterSettings.initial_hosts, ":5001" )
                .newGraphDatabase();

//        registerShutdownHook(db);
    }


    public void doStuff() throws MalformedObjectNameException, IntrospectionException, InstanceNotFoundException, ReflectionException, IOException {
        List<JmxItem> jmxItems = new ArrayList<>();
        AsciiDocListGenerator listGenerator = new AsciiDocListGenerator("jmx-list", "MBeans exposed by Neo4j", false);

        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        SortedMap<String, ObjectName> neo4jBeans = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (String query : QUERIES) {
            Set<ObjectInstance> beans = mBeanServer.queryMBeans(new ObjectName(query), null);
            for (ObjectInstance bean : beans) {
                ObjectName objectName = bean.getObjectName();
                String name = objectName.getKeyProperty(BEAN_NAME);
                if (EXCLUDES.contains(name)) {
                    continue;
                }
                String name0 = objectName.getKeyProperty(BEAN_NAME0);
                if (name0 != null) {
                    name += '/' + name0;
                }
                neo4jBeans.put(name, bean.getObjectName());
            }
        }

        System.out.printf("  [+] number of beans found: %d%n", neo4jBeans.size());

        for (Map.Entry<String, ObjectName> beanEntry : neo4jBeans.entrySet()) {
            ObjectName objectName = beanEntry.getValue();
            String name = beanEntry.getKey();
            Set<ObjectInstance> mBeans = mBeanServer.queryMBeans(objectName, null);
            if (mBeans.size() != 1) {
                throw new IllegalStateException(String.format("Unexpected size [%d] of query result for [%s].", mBeans.size(), objectName));
            }
            ObjectInstance bean = mBeans.iterator().next();
            MBeanInfo info = mBeanServer.getMBeanInfo(objectName);
            String description = info.getDescription().replace('\n', ' ');

            String id = toId(name);
            JmxItem item = new JmxItem(id, name, Optional.of(description));
            System.out.printf("    [*] name: %s%n", name);
            System.out.printf("        id: %s%n", id);
            System.out.printf("        description: %s%n", description);
            System.out.printf("        %s%n", item);

//            settingDescriptions.add( new SettingDescriptionImpl( id, name, Optional.of(description) ) );
            printDetails(id, objectName, bean, info, description);
        }

        Writer fw = null;
        try {
            fw = AsciiDocGenerator.getFW("target/docs/ops", "JMX List");
            fw.write(listGenerator.generateListAndTableCombo(jmxItems));
        } finally {
            if (fw != null) {
                fw.close();
            }
        }
    }

    private String toId(String name) {
        return "jmx-" + name.replace( ' ', '-' ).replace( '/', '-' ).toLowerCase();
    }

    private void printDetails(String id, ObjectName objectName, ObjectInstance bean, MBeanInfo info, String description) throws IOException {
        StringBuilder beanInfo = new StringBuilder(2048);
        String name = objectName.getKeyProperty(BEAN_NAME);
        String name0 = objectName.getKeyProperty(BEAN_NAME0);
        if (name0 != null) {
            name += "/" + name0;
        }

        MBeanAttributeInfo[] attributes = info.getAttributes();
        beanInfo.append("[[").append(id).append("]]\n");
        if (attributes.length > 0) {
            beanInfo.append(".MBean ").append(name).append(" (").append(bean.getClassName()).append(") Attributes\n");
            printAttributesTable(description, beanInfo, attributes, false);
            printAttributesTable(description, beanInfo, attributes, true);
            beanInfo.append("\n");
        }

        MBeanOperationInfo[] operations = info.getOperations();
        if (operations.length > 0) {
            beanInfo.append(".MBean ").append(name).append(" (").append(bean.getClassName()).append(") Operations\n");
            printOperationsTable(beanInfo, operations, false);
            printOperationsTable(beanInfo, operations, true);
            beanInfo.append("\n");
        }

        if (beanInfo.length() > 0) {
//            System.out.printf("    [---] %s%n", beanInfo.toString());
            Writer fw = null;
            try {
                fw = AsciiDocGenerator.getFW("target/docs/ops", id);
                fw.write(beanInfo.toString());
            } finally {
                if (fw != null) {
                    fw.close();
                }
            }
        }
    }

    private void printAttributesTable(String description, StringBuilder beanInfo, MBeanAttributeInfo[] attributes, boolean nonHtml) {
        beanInfo.append(nonHtml ? IFDEF_NONHTMLOUTPUT : IFDEF_HTMLOUTPUT);
        beanInfo.append("[options=\"header\", cols=\"20m,36,20m,7,7\"]\n")
                .append("|===\n")
                .append("|Name|Description|Type|Read|Write\n")
                .append("5.1+^e|").append(description).append('\n');
        SortedSet<String> attributeInfo = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (MBeanAttributeInfo attrInfo : attributes) {
            StringBuilder attributeRow = new StringBuilder(512);
            String type = getType(attrInfo.getType());
            Descriptor descriptor = attrInfo.getDescriptor();
            type = getCompositeType(type, descriptor, nonHtml);
            attributeRow.append('|')
                    .append( makeBreakable( attrInfo.getName(), nonHtml ) )
                    .append( '|' )
                    .append( attrInfo.getDescription()
                            .replace( '\n', ' ' ) )
                    .append( '|' )
                    .append( type )
                    .append( '|' )
                    .append( attrInfo.isReadable() ? "yes" : "no" )
                    .append( '|' )
                    .append( attrInfo.isWritable() ? "yes" : "no" )
                    .append( '\n' );
            attributeInfo.add( attributeRow.toString() );
        }
        for ( String row : attributeInfo )
        {
            beanInfo.append( row );
        }
        beanInfo.append( "|===\n" );
        beanInfo.append( ENDIF );

    }

    private void printOperationsTable(StringBuilder beanInfo, MBeanOperationInfo[] operations, boolean nonHtml) {
        beanInfo.append(nonHtml ? IFDEF_NONHTMLOUTPUT : IFDEF_HTMLOUTPUT);
        beanInfo.append("[options=\"header\", cols=\"23m,37,20m,20m\"]\n")
                .append("|===\n")
                .append("|Name|Description|ReturnType|Signature\n");
        SortedSet<String> operationInfo = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (MBeanOperationInfo operInfo : operations) {
            StringBuilder operationRow = new StringBuilder(512);
            String type = getType(operInfo.getReturnType());
            Descriptor descriptor = operInfo.getDescriptor();
            type = getCompositeType(type, descriptor, nonHtml);
            operationRow.append('|')
                    .append(operInfo.getName())
                    .append('|')
                    .append(operInfo.getDescription()
                            .replace('\n', ' '))
                    .append('|')
                    .append(type)
                    .append('|');
            MBeanParameterInfo[] params = operInfo.getSignature();
            if (params.length > 0) {
                for (int i = 0; i < params.length; i++) {
                    MBeanParameterInfo param = params[i];
                    operationRow.append(param.getType());
                    if (i != (params.length - 1)) {
                        operationRow.append(',');
                    }
                }
            } else {
                operationRow.append("(no parameters)");
            }
            operationRow.append('\n');
            operationInfo.add(operationRow.toString());
        }
        for (String row : operationInfo) {
            beanInfo.append(row);
        }
        beanInfo.append("|===\n");
        beanInfo.append(ENDIF);
    }

    private String getCompositeType(String type, Descriptor descriptor, boolean nonHtml) {
        String newType = type;
        if ("javax.management.openmbean.CompositeData[]".equals(type)) {
            Object originalType = descriptor.getFieldValue("originalType");
            if (originalType != null) {
                newType = getLinkedType(getType((String) originalType), nonHtml);
                if (nonHtml) {
                    newType += " as CompositeData[]";
                } else {
                    newType += " as http://docs.oracle.com/javase/7/docs/api/javax/management/openmbean/CompositeData.html"
                            + "[CompositeData][]";
                }
            }
        }
        return newType;
    }

    private String getType(String type) {
        if (TYPES.containsKey(type)) {
            return TYPES.get(type);
        } else if (type.endsWith(";")) {
            if (type.startsWith("[L")) {
                return type.substring(2, type.length() - 1) + "[]";
            } else {
                throw new IllegalArgumentException("Don't know how to parse this type: " + type);
            }
        }
        return type;
    }

    private String getLinkedType(String type, boolean nonHtml) {
        if (!type.startsWith("org.neo4j")) {
            if (!type.startsWith("java.util.List<org.neo4j.")) {
                return type;
            } else {
                String typeInList = type.substring(15, type.length() - 1);
                return "java.util.List<" + getLinkedType(typeInList, nonHtml) + ">";
            }
        } else if (nonHtml || type.startsWith("org.neo4j.kernel")) {
            return type;
        } else {
            StringBuilder url = new StringBuilder(160);
            url.append(JAVADOC_URL);
            String typeString = type;
            if (type.endsWith("[]")) {
                typeString = type.substring(0, type.length() - 2);
            }
            url.append(typeString.replace('.', '/'))
                    .append(".html[")
                    .append(typeString)
                    .append("]");
            if (type.endsWith("[]")) {
                url.append("[]");
            }
            return url.toString();
        }
    }

    private String makeBreakable(String name, boolean nonHtml) {
        if (nonHtml) {
            return name.replace( "_", "_\u200A" )
                    .replace( "NumberOf", "NumberOf\u200A" )
                    .replace( "InUse", "\u200AInUse" )
                    .replace( "Transactions", "\u200ATransactions" );
        } else {
            return name;
        }
    }

//    private static void registerShutdownHook( final GraphDatabaseService graphDb ) {
//        Runtime.getRuntime().addShutdownHook(new Thread() {
//            @Override
//            public void run() {
//                graphDb.shutdown();
//            }
//        });
//    }

    public void shutdown() {
        if ( db != null ) {
            db.shutdown();
        } else {
            System.out.println("  hey man dont shut me down twice!");
        }
        db = null;
    }

    public static void main(String[] args) throws MalformedObjectNameException, IntrospectionException, ReflectionException, InstanceNotFoundException, IOException {
        System.out.println("begin..");
        JmxTool tool = new JmxTool();
        try {
            System.out.println("about to do stuff");
            tool.doStuff();
            System.out.println("did stuff");
        } catch (MalformedObjectNameException|IntrospectionException|ReflectionException|IOException ex) {
            System.out.println("ERROR ERROR");
            throw ex;
        } finally {
            tool.shutdown();
        }
        System.out.println("..done");
    }

}