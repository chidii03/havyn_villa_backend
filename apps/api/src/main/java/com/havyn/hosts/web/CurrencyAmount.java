package com.havyn.hosts.web;

import java.math.BigDecimal;

public record CurrencyAmount(String currency, BigDecimal amount) {
}
