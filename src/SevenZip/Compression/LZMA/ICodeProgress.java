package SevenZip.Compression.LZMA;

public interface ICodeProgress
{
    public void SetProgress(long inSize, long outSize);
}
