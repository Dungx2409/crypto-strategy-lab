package com.cryptolab.infrastructure.news.adapter.cryptocompare;

import java.net.URI;

interface CryptoCompareTransport {

    String get(URI uri);
}
