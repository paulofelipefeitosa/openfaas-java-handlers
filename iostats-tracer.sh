TYPE=$1
TRACER_OUTPUT=$2

if [ $TYPE == "run" ];
then
	pkill -f "python biosnoop.py"
	>&2 echo "Running I/O stats BCC probes"

	python -u probes-specs/biosnoop.py > $TRACER_OUTPUT &
	TRACER_PID=$!
	>&2 echo "BCC Tracer PID=$TRACER_PID"

	while [ $(wc -c "$TRACER_OUTPUT" | awk '{print $1}') -eq 0 ];
	do
		sleep 1
	done

	echo "$TRACER_PID"
elif [ $TYPE == "parse" ];
then
	# TODO
else
	echo "Cannot identify tracer behavior type [$TYPE]"
	exit 1
fi