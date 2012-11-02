# Create Indices

Create and configure your elasticsearch index like this. Here `ES_HOST` is one of your elasticsearch nodes.

    > curl -XPUT 'http://$ES_HOST:9200/bugs' -d @bug_version.json

