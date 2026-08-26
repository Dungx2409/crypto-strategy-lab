package com.cryptolab.infrastructure.news.adapter.html;

import java.net.URI;

interface HtmlTransport {
    String get(URI uri);
}
