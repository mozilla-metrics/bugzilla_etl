# Create Indices

Create and configure your elasticsearch index like this. Here `localhost` is one of your elasticsearch nodes.

    > curl -XPUT 'http://localhost:9200/bugs' -d @bugs.json

