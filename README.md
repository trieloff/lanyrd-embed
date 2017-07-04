# lanyrd-embed
Adding OEmbed Support for Lanyrd

## Development

### Prerequisites

As Lanyrd does not include the country in the address returned by the `ics` file used by
`lanyrd-embed`, we fall back to using the Bing Maps reverse geocoder. This geocoder needs
an API key. You can get a free API key from the [Bing Maps Portal](https://www.bingmapsportal.com).

During development, tests will fail if the `BING_MAPS_KEY` environment variable is not
set, so add this command to your `.profile` to pick it up:

```bash
export BING_MAPS_KEY
```

When setting up GitHub-based deployment for Adobe I/O Runtime, do not forget to include
the Bing Maps key as URL parameter `key`.