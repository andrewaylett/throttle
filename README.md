# Throttle

As a service client, there's little point in sending requests to the service that we can be reasonably sure will fail.

This is a simple library
that provides a way to throttle requests to a service based on the number of requests that have been sent in the past.
It uses a sliding window algorithm to determine if a request should be sent or not.

The concept comes from chapter 21 of the [SRE book](https://sre.google/sre-book/handling-overload/).
The author has previously released [self-throttle](https://www.npmjs.com/package/self-throttle), for Node.js.
This library is a new implementation of the same concept, rather than a port.

## Usage

Include the library in your dependencies:

<!-- [[[cog
result = sp.run(
    ["./gradlew", "-q", "printVersion"],
    capture_output=True,
    text=True,
    check=True
)
version = result.stdout.strip()
print(f"""```groovy
dependencies {{
    implementation 'eu.aylett.throttle:throttle:{version}'
}}
```""")
]]] -->
<!-- [[[end]]] -->

