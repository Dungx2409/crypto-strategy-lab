export function stompFrameText(message) {
    if (typeof message === "string") return message;
    if (message && typeof message.data === "string") return message.data;
    return String(message?.data ?? message ?? "");
}

export function marketTimeframe(frame) {
    const destination = frame.match(/^destination:\/topic\/market\/[^/]+\/([^\n\r]+)$/m);
    return destination ? destination[1] : null;
}
