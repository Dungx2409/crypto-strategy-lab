package com.cryptolab.shared.port;

import com.cryptolab.shared.domain.OperationalStatusSnapshot;

public interface OperationalStatusProvider {

    OperationalStatusSnapshot current();
}
