package com.elnourpower.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "app")
class AppProperties {
    var defaultCity: String = "Tunis"
    var defaultLat: Double = 36.81897
    var defaultLon: Double = 10.16579
    var outageHours: Int = 48
}
