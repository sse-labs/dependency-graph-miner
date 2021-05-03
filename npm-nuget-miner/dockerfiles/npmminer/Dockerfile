FROM python:3.8.2-alpine3.11	

ENV PYTHONUNBUFFERED=1

RUN pip install neo4j
RUN pip install requests
RUN pip install ijson

COPY ./dependencygraphutils/ /usr/app/npm_miner/dependencygraphutils
COPY ./referenceresolving/ /usr/app/npm_miner/referenceresolving
COPY ./postprocessing/ /usr/app/npm_miner/postprocessing
COPY ./npmanalysis/ /usr/app/npm_miner/npmanalysis
COPY ./npmminer.py /usr/app/npm_miner/npmminer.py

WORKDIR /usr/app/npm_miner

ENTRYPOINT python /usr/app/npm_miner/npmminer.py