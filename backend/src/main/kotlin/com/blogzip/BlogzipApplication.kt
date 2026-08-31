package com.blogzip

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication
class BlogzipApplication

fun main(args: Array<String>) {
    runApplication<BlogzipApplication>(*args)
}
