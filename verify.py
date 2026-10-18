"""Validate events from actual Java executions, not synthetic examples."""
import json, pathlib
ROOT=pathlib.Path(__file__).resolve().parent
MODES=['philosophers-semaphore','philosophers-monitor','rw-semaphore','rw-monitor']
for mode in MODES:
    rows=[json.loads(x) for x in (ROOT/'results'/f'{mode}.jsonl').read_text().splitlines()]
    assert rows[0]['state']=='started' and rows[-1]['state']=='complete'
    assert [r['seq'] for r in rows]==list(range(1,len(rows)+1))
    assert all(a['ms']<=b['ms'] for a,b in zip(rows,rows[1:]))
    if mode.startswith('philosophers'):
        eating={};max_eaters=0
        for r in rows:
            a,s=r['actor'],r['state']
            if not a.startswith('P'):continue
            if s=='eating':
                p=int(a[1:]);assert p not in eating
                assert all(q not in {(p+1)%4,(p-1)%4} for q in eating),f'adjacent eaters {p},{eating}'
                eating[p]=1;max_eaters=max(max_eaters,len(eating))
            if s in ('releasing','finished'): eating.pop(int(a[1:]),None)
        assert not eating
        assert sum(r['state']=='eating' for r in rows)==12
    else:
        reading=set();writing=set()
        for r in rows:
            a,s=r['actor'],r['state']
            if a[0:1]=='R':
                if s=='reading':assert not writing;reading.add(a)
                if s in ('leaving','finished'):reading.discard(a)
            if a[0:1]=='W':
                if s=='writing':assert not writing and not reading;writing.add(a)
                if s in ('leaving','finished'):writing.discard(a)
        assert not reading and not writing
        assert sum(r['state']=='reading' for r in rows)==5
        assert sum(r['state']=='writing' for r in rows)==3
    print(f'{mode}: PASS | {len(rows)} events | {rows[-1]["ms"]} ms')
