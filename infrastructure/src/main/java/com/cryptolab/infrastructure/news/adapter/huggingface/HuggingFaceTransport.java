package com.cryptolab.infrastructure.news.adapter.huggingface;

import java.net.URI;

interface HuggingFaceTransport {
    String classify(URI endpoint, String token, String jsonBody);
}
