FROM python:3.8.2-alpine3.11	

ENV PYTHONUNBUFFERED=1

RUN pip install neo4j
RUN pip install requests
RUN pip install ijson

COPY ./dependencygraphutils/ /usr/app/nuget_miner/dependencygraphutils
COPY ./referenceresolving/ /usr/app/nuget_miner/referenceresolving
COPY ./postprocessing/ /usr/app/nuget_miner/postprocessing
COPY ./nugetanalysis/ /usr/app/nuget_miner/nugetanalysis
COPY ./nugetminer.py /usr/app/nuget_miner/nugetminer.py

WORKDIR /usr/app/nuget_miner

ENTRYPOINT python /usr/app/nuget_miner/nugetminer.py