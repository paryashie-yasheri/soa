package ru.ifmo.soa

import jakarta.servlet.ServletContextEvent
import jakarta.servlet.ServletContextListener
import jakarta.servlet.annotation.WebListener
import liquibase.Contexts
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import javax.naming.InitialContext
import javax.sql.DataSource

/**
 * Applies the Liquibase changelog against the shared schema before the application serves traffic.
 * The schema itself already exists on the target database, so only tables and indexes are managed here.
 */
@WebListener
class LiquibaseMigrationListener : ServletContextListener {
    override fun contextInitialized(event: ServletContextEvent) {
        val dataSource = InitialContext().lookup(DATA_SOURCE) as DataSource
        dataSource.connection.use { connection ->
            val database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(JdbcConnection(connection))
                .apply {
                    defaultSchemaName = SCHEMA
                    liquibaseSchemaName = SCHEMA
                }
            ClassLoaderResourceAccessor(javaClass.classLoader).use { resources ->
                Liquibase("db/changelog/db.changelog-master.xml", resources, database).use { liquibase ->
                    liquibase.update(Contexts())
                }
            }
        }
    }

    override fun contextDestroyed(event: ServletContextEvent) = Unit

    private companion object {
        const val DATA_SOURCE = "java:jboss/datasources/SoaDS"
        const val SCHEMA = "s389491"
    }
}
