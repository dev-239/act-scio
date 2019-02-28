import json
import sys


def run(input, output):
    with open(input, 'r') as in_fp:
        data = json.load(in_fp)

        threat_actors = []
        for value in data['values']:
            name = value['value']
            aliases = value.get('meta', {}).get('synonyms', [])
            threat_actor = u"{}: {}".format(name, ','.join(aliases))
            threat_actors.append(threat_actor)

        with open(output, 'wb') as out_fp:
            for threat_actor in threat_actors:
                out_fp.write(threat_actor.encode('utf-8'))
                out_fp.write('\n'.encode('utf-8'))


if __name__ == '__main__':
    if len(sys.argv) != 3:
        print("Usage: threat_actors_to_cfg.py <input> <output>")
    else:
        run(input=sys.argv[1], output=sys.argv[2])