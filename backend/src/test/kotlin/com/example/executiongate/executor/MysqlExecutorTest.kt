package com.example.executiongate.executor

import com.example.executiongate.service.ExecutorService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

@SpringBootTest
class MysqlExecutorTest(
    @Autowired override val executorService: ExecutorService,
) : AbstractExecutorTest(
    executorService = executorService,
) {

    companion object {
        val db: MySQLContainer<*> = MySQLContainer(DockerImageName.parse("mysql:8"))
            .withUsername("root")
            .withPassword("")
            .withReuse(true)
            .withDatabaseName("")

        init {
            db.start()
        }
    }

    override fun getDb(): JdbcDatabaseContainer<*> = db

    override val initScript: String = "mysql_init.sql"
}
